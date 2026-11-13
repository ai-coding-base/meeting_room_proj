# DB Schema 设计说明 v1.0

> 配套：`schema.sql`  
> 数据库：MySQL 8.0+ / InnoDB / utf8mb4

---

## 1. 表清单

| # | 表名 | 用途 | 关键索引 |
|---|---|---|---|
| 1 | `users` | 员工 | `uk_username`, `idx_superior` |
| 2 | `departments` | 部门树 | `idx_parent` |
| 3 | `delegation_grants` | 代订授权 | `uk_delegate_principal` |
| 4 | `rooms` | 会议室 | `idx_status`, `idx_capacity` |
| 5 | `time_slots` | 时间槽占用（核心防超卖）| **`uk_room_slot` ← 唯一约束兜底** |
| 6 | `bookings` | 预订主表 | `idx_room_time`, `idx_principal_time` |
| 7 | `idempotency_keys` | 幂等防重 | PK = `request_id` |
| 8 | `audit_logs` | 审计 | `idx_operator_time`, `idx_target` |
| 9 | `notifications` | 通知发送记录 | `idx_status_retry` |

---

## 2. 关键设计决策

### 2.1 防超卖：TimeSlot 唯一约束

**核心思路**：将连续时间区间打散为 30 分钟槽位，对 `(room_id, slot_start)` 建唯一索引。

```
预订 09:00–10:30 → 写入 3 行 time_slots：
  (room_1, 09:00, 09:30, booking_42)
  (room_1, 09:30, 10:00, booking_42)
  (room_1, 10:00, 10:30, booking_42)

并发用户 B 抢 09:30–10:00：
  INSERT (room_1, 09:30, ..., booking_43) → DuplicateKey → 业务报错"该时段已被预订"
```

**优势**
- 不依赖分布式锁，DB 层原子保证
- 写入冲突立即失败，不会出现"读时可用，写时被抢"的幻读
- 取消时直接 `DELETE FROM time_slots WHERE booking_id = ?`，立即释放

**约束**
- `slot_start` 必须严格对齐 `:00` 或 `:30`（应用层校验）
- 一次预订写多行：用事务包裹，全部成功或全部失败

### 2.2 时长 ≥ 15 分钟与 30 分钟槽位的协调

业务允许 **15 分钟最短时长 + 任意起止时间**，但底层槽位是 30 分钟。

**协调方案**：
- 任意起止时间在 `bookings.start_time / end_time` 中精确存储
- 槽位占用按"覆盖范围"向上取整：占用所有相交的 30 分钟槽位
- 例：09:10–09:25（15 min）→ 占用 `09:00–09:30` 一个槽位

> ⚠️ **副作用**：09:10–09:25 占用整个 09:00–09:30 后，他人无法再订 09:30 之前的任何分钟。这是粒度选择必然的代价；如不接受可改为 15 分钟槽位（成本 = TimeSlot 表数据量翻倍）。

### 2.3 幂等：客户端 request_id

```
POST /bookings
Body: { "request_id": "uuid-v4", ...其他字段 }
```

**服务端流程**
```
BEGIN TX
  INSERT INTO idempotency_keys(request_id, user_id, endpoint, expires_at)
    VALUES(?, ?, 'POST /bookings', NOW() + INTERVAL 24 HOUR)
    -- 主键冲突 → 直接 SELECT 已有记录返回 response
  执行预订写入
  UPDATE idempotency_keys SET booking_id=?, response=? WHERE request_id=?
COMMIT
```

定时任务每日清理 `expires_at < NOW()` 的记录。

### 2.4 软删除策略

- 所有"业务实体表"（users / rooms / bookings / departments / delegation_grants）含 `deleted_at`
- 唯一索引使用三元组：`UNIQUE(name, deleted_at)`，允许同名实体被软删后重建
- `time_slots` **不**软删除：物理删除即代表释放，避免唯一约束冲突
- `audit_logs` / `notifications` **不**软删除：append-only

### 2.5 上级取消下级的字段约束

`bookings.cancel_reason` 在 SQL 层不强制 NOT NULL（因本人取消不需要原因），由应用层根据 `cancelled_by != booker_id` 判断后强制要求填写。

---

## 3. 状态机映射

### 3.1 Booking.status

```
        提交
         │
   ┌─────┴──────┐
   │ 普通会议室  │ VIP/大型
   ▼            ▼
CONFIRMED    PENDING ──审批通过→ CONFIRMED
                  └──审批驳回→ REJECTED（终态）
   │            │
   └─────┬──────┘
         │ 取消
         ▼
     CANCELLED（软删 + cancel_reason）
         │
   开始时间到 + 未取消
         ▼
     COMPLETED（定时任务流转）
```

### 3.2 Room.status

```
AVAILABLE ⇄ MAINTENANCE
     └──→ DECOMMISSIONED（终态，不可逆）

进入 MAINTENANCE/DECOMMISSIONED 时：
  1. 查询所有 status IN (PENDING, CONFIRMED) AND start_time > NOW() 的 booking
  2. 批量 UPDATE bookings SET status='CANCELLED', cancel_reason='会议室维修', cancelled_by=管理员id
  3. 删除对应 time_slots
  4. 通过 5.3 三通道通知预订人
```

---

## 4. 性能与索引

| 高频查询 | 命中索引 |
|---|---|
| 列出某会议室某天的占用 | `time_slots.idx_room_range` |
| 列出某用户的预订历史 | `bookings.idx_principal_time` |
| 列出代订人发起的预订 | `bookings.idx_booker_time` |
| 后台按时间区间查所有预订 | `bookings.idx_room_time` |
| 审计日志按操作人查询 | `audit_logs.idx_operator_time` |
| 通知重试扫描 | `notifications.idx_status_retry` |

---

## 5. 容量估算（参考）

假设：500 员工、50 间会议室、平均每天 200 次预订、平均时长 1 小时（即 2 个槽位）。

| 表 | 一年数据量 |
|---|---|
| `bookings` | 200 × 365 ≈ 7.3 万行 |
| `time_slots` 活跃记录 | 同时段最多 200 × 2 = 400 行；过期可清理 |
| `audit_logs` | 200 × 3（创建/审批/取消）× 365 ≈ 22 万行/年 |
| `notifications` | 200 × 3 通道 × 2 事件 × 365 ≈ 44 万行/年 |

单库 MySQL 5 年内无压力；后续若 `audit_logs` 过大可按月分区。

---

## 6. 后续优化（v1.1+）

- `time_slots` 历史数据归档（已结束 30 天后转入冷表）
- `audit_logs` 按月分区
- 引入 Redis 缓存"今日某会议室占用情况"，TTL 1 分钟
- 槽位粒度可配置化（30/15 min 切换）
