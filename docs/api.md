# 会议室预订系统 API 设计 v1.0

> 依据：`prd.md` v1.0 · `schema.sql` v1.0
> 协议：HTTP/1.1 + JSON
> BaseURL：`/api/v1`（v1.0 可省略 `/v1`）

---

## 1. 通用约定

### 1.1 必备 Header

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | ✅ | `Bearer <jwt>` |
| `X-Request-Id` | 写操作必填 | 客户端 UUID，幂等 + 链路追踪 |
| `Content-Type` | POST/PUT/PATCH | `application/json;charset=utf-8` |

### 1.2 统一响应

**成功**
```json
{ "code": 0, "msg": "ok", "data": { ... }, "traceId": "abc123" }
```

**失败**
```json
{ "code": "BOOKING_CONFLICT", "msg": "该时段已被预订", "data": null, "traceId": "abc123" }
```

### 1.3 HTTP 状态码

| Code | 语义 |
|---|---|
| 200 | 业务成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 / Token 失效 |
| 403 | 已登录但无权限 |
| 404 | 资源不存在 |
| 409 | 业务冲突（时段冲突、状态非法） |
| 429 | 限流 |
| 500 | 服务端异常 |

### 1.4 通用错误码

| code | HTTP | 说明 |
|---|---|---|
| `INVALID_PARAM` | 400 | 参数校验失败（含校验详情） |
| `UNAUTHORIZED` | 401 | 未登录或 Token 失效 |
| `FORBIDDEN` | 403 | 无操作权限 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `RATE_LIMITED` | 429 | 触发限流 |
| `INTERNAL_ERROR` | 500 | 系统繁忙 |

### 1.5 分页约定

```
GET /api/xxx?page=1&size=20
```

```json
{
  "code": 0,
  "data": {
    "items": [ ... ],
    "page": 1,
    "size": 20,
    "total": 123
  }
}
```

---

## 2. 接口列表

| # | Method | Path | 描述 | 关联 US |
|---|---|---|---|---|
| 1 | GET    | `/api/rooms` | 列出会议室 | US-01 |
| 2 | GET    | `/api/rooms/{id}/availability` | 查询会议室某天占用 | US-01 |
| 3 | POST   | `/api/bookings` | 创建预订 | US-02 / US-03 |
| 4 | GET    | `/api/bookings` | 列出我的预订 | US-01 |
| 5 | GET    | `/api/bookings/{id}` | 预订详情 | US-01 |
| 6 | DELETE | `/api/bookings/{id}` | 取消预订 | US-05 |
| 7 | POST   | `/api/bookings/{id}/approve` | 审批预订（VIP/大型） | US-04 |

> v1.0 优先实现 **#3 创建预订**（含幂等 + 防超卖 + 审批分流），其他接口按 `prd.md` 后续迭代。

---

## 3. 接口详情

### 3.1 创建预订（核心）

- **Path**：`POST /api/bookings`
- **Auth**：必需
- **幂等**：是（同一 `X-Request-Id` 重复请求返回首次结果，TTL 24h）
- **关联 US**：US-02（自订）/ US-03（代订）
- **关联文档**：`prd.md` §3 / `schema.md` §2.1 防超卖

#### Request Header
```
Authorization: Bearer <jwt>
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
```

#### Request Body

```json
{
  "roomId": 1,
  "principalId": null,
  "subject": "Q2 业务复盘会",
  "startTime": "2026-05-25T14:00:00",
  "endTime": "2026-05-25T15:00:00",
  "attendeeCount": 8,
  "attendeeIds": [1001, 1002, 1003],
  "visibility": "PUBLIC"
}
```

| 字段 | 类型 | 必填 | 校验 | 说明 |
|---|---|---|---|---|
| `roomId` | long | ✅ | > 0 | 会议室 ID |
| `principalId` | long | ❌ | > 0 | 被代订人 ID；不传则等于当前用户（自订） |
| `subject` | string | ✅ | 1-255 字 | 会议主题 |
| `startTime` | datetime | ✅ | 未来时间 / 对齐 30 分钟槽 / 不超过 NOW + 7 天 | ISO8601 不带时区 |
| `endTime` | datetime | ✅ | > startTime / 时长 ≥ 15min | 同上 |
| `attendeeCount` | int | ✅ | ≥ 1 / ≤ room.capacity | 实际参会人数 |
| `attendeeIds` | long[] | ❌ | 元素 > 0 | 参会人 ID 列表 |
| `visibility` | enum | ❌ | `PUBLIC`/`PRIVATE` | 默认 `PUBLIC` |

#### Response 200

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "id": 42,
    "roomId": 1,
    "roomName": "高管会议室 A",
    "bookerId": 1001,
    "principalId": 1001,
    "subject": "Q2 业务复盘会",
    "startTime": "2026-05-25T14:00:00",
    "endTime": "2026-05-25T15:00:00",
    "attendeeCount": 8,
    "visibility": "PUBLIC",
    "status": "CONFIRMED",
    "needApproval": false,
    "createdAt": "2026-05-23T21:50:00"
  },
  "traceId": "abc123"
}
```

#### 业务行为说明

| 场景 | 行为 |
|---|---|
| 普通会议室（capacity ≤ 50 且非 VIP） | `status = CONFIRMED`，`needApproval = false` |
| VIP 或 capacity > 50 | `status = PENDING`，`needApproval = true`，触发审批通知 |
| 代订（`principalId` ≠ 当前用户） | 必须有 `delegation_grants` 授权；代订 VIP/大型仍走审批 |
| 同一 `X-Request-Id` 重放 | 直接返回首次创建的 booking |
| 时段冲突 | 返回 409 / `BOOKING_CONFLICT` |
| 会议室维修中 | 返回 409 / `ROOM_NOT_AVAILABLE` |

#### 错误码

| code | HTTP | 触发条件 |
|---|---|---|
| `INVALID_PARAM` | 400 | 参数校验失败（含校验详情） |
| `INVALID_TIME` | 400 | 时间不对齐 30 分钟 / 时长 < 15min / 超过 7 天 / 过去时间 |
| `CAPACITY_EXCEEDED` | 400 | `attendeeCount > room.capacity` |
| `ROOM_NOT_FOUND` | 404 | 会议室不存在或已下线 |
| `ROOM_NOT_AVAILABLE` | 409 | 会议室处于 MAINTENANCE / DECOMMISSIONED |
| `BOOKING_CONFLICT` | 409 | 时段已被占用（DB 唯一约束触发） |
| `DELEGATION_DENIED` | 403 | 代订时无授权关系 |
| `DEPARTMENT_DENIED` | 403 | 部门专属会议室，非本部门员工 |

#### cURL 示例

```bash
curl -X POST https://api.example.com/api/bookings \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Request-Id: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": 1,
    "subject": "Q2 业务复盘会",
    "startTime": "2026-05-25T14:00:00",
    "endTime": "2026-05-25T15:00:00",
    "attendeeCount": 8,
    "visibility": "PUBLIC"
  }'
```

---

### 3.2 查询会议室可用性

- **Path**：`GET /api/rooms/{id}/availability?date=2026-05-25`
- **Auth**：必需
- **关联 US**：US-01

#### Response 200

```json
{
  "code": 0,
  "data": {
    "roomId": 1,
    "date": "2026-05-25",
    "occupiedSlots": [
      { "start": "09:00", "end": "10:00", "bookingId": 41, "subject": "晨会", "visible": true },
      { "start": "14:00", "end": "15:00", "bookingId": 42, "subject": null, "visible": false }
    ]
  }
}
```

> `visible=false` 时 `subject=null`，符合 `prd.md` §4.1 数据可见性规则。

---

### 3.3 列出会议室

- **Path**：`GET /api/rooms`
- **Query**：`floor` / `area` / `minCapacity` / `equipment` / `keyword`
- **Auth**：必需
- **关联 US**：US-01

---

### 3.4 列出我的预订

- **Path**：`GET /api/bookings`
- **Query**：`scope=mine|delegated|subordinate` / `status` / `from` / `to` / `page` / `size`
- **Auth**：必需
- **关联 US**：US-01

---

### 3.5 预订详情

- **Path**：`GET /api/bookings/{id}`
- **Auth**：必需
- **可见性规则**：predicate = (本人 / 被代订人 / 上级 / 管理员 / `visibility=PUBLIC`) → 显示完整；否则仅显示时段占用

---

### 3.6 取消预订

- **Path**：`DELETE /api/bookings/{id}`
- **Auth**：必需
- **关联 US**：US-05

#### Request Body

```json
{ "reason": "会议改期" }
```

| 字段 | 必填条件 |
|---|---|
| `reason` | 上级取消下级 / 管理员强制取消 → 必填，≥ 5 字 |

#### 错误码
| code | HTTP | 说明 |
|---|---|---|
| `BOOKING_NOT_FOUND` | 404 | 预订不存在 |
| `CANCEL_DENIED` | 403 | 无取消权限（非本人/上级/管理员） |
| `REASON_REQUIRED` | 400 | 强制取消未填原因 |
| `BOOKING_ALREADY_CANCELLED` | 409 | 已取消 |

---

### 3.7 审批预订

- **Path**：`POST /api/bookings/{id}/approve`
- **Auth**：审批人
- **关联 US**：US-04

#### Request Body

```json
{ "approve": true, "rejectReason": null }
```

驳回时 `approve=false` 且 `rejectReason` 必填。

---

## 4. 限流策略（v1.1 计划）

| 接口 | 限流 |
|---|---|
| `POST /api/bookings` | 单用户 10 次/分钟 |
| `GET /api/rooms/*/availability` | 单用户 60 次/分钟 |
| 全局 | 1000 QPS |

---

## 5. 变更日志

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-05-23 | 初版，定义全部 7 个端点；v1.0 实现 #3 |
