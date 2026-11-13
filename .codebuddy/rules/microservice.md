---
description: 微服务工程化规范：API 契约、可观测性、容错、配置管理。AI 在涉及服务间调用、运维诉求时加载。
globs: backend/**/*.java
alwaysApply: false
---

# 微服务工程化规范

> 适用范围：所有对外/对内的服务接口、跨服务调用、运维相关代码

---

## 1. API 契约（REST）

### 1.1 URL 与方法

| 操作 | 方法 | 路径示例 |
|---|---|---|
| 列表 | GET | `/api/bookings?date=2026-05-23&roomId=1` |
| 详情 | GET | `/api/bookings/{id}` |
| 创建 | POST | `/api/bookings` |
| 更新 | PUT | `/api/bookings/{id}`（全量） |
| 局部更新 | PATCH | `/api/bookings/{id}/status` |
| 删除/取消 | DELETE | `/api/bookings/{id}` |

**约束**：
- 路径用复数名词 + kebab-case
- 嵌套不超过 2 层（`/rooms/{id}/bookings` 可，`/depts/{a}/rooms/{b}/bookings` 不可）
- 动作类用子资源（`/bookings/{id}/cancel` 而非 `/cancelBooking`）

### 1.2 统一响应

```json
// 成功
{ "code": 0, "msg": "ok", "data": { ... }, "traceId": "..." }

// 失败
{ "code": "BOOKING_CONFLICT", "msg": "该时段已被预订", "data": null, "traceId": "..." }
```

**HTTP 状态码语义**：
- `200`：业务成功
- `400`：参数错误
- `401`：未登录
- `403`：无权限
- `404`：资源不存在
- `409`：业务冲突（如预订冲突）
- `429`：限流
- `500`：服务端异常

> **不**用 200 + body code 表示所有业务错误，**HTTP 状态码必须语义正确**。

### 1.3 必备 Header

| Header | 说明 |
|---|---|
| `X-Request-Id` | 客户端 UUID，幂等 + 链路追踪 |
| `Authorization` | `Bearer <token>` |
| `X-Trace-Id` | 由网关注入，跨服务透传 |

---

## 2. 版本管理

- URL 版本：`/api/v1/bookings`（v1.0 可省略 `/v1`，v2 必须显式）
- 不兼容变更必须升版本，不在原版本内删字段
- 新字段加 `@JsonInclude(NON_NULL)`，老客户端忽略即可

---

## 3. 服务间调用

### 3.1 优先级

1. **能不调就不调**：用事件驱动 / 物化视图减少同步依赖
2. 同步调用用 REST（OpenFeign）
3. 高性能/高并发场景用 gRPC（带契约定义）

### 3.2 容错三件套（强制）

```java
@FeignClient(name = "user-service",
             fallbackFactory = UserClientFallback.class,
             configuration = FeignConfig.class)
public interface UserClient {
    @GetMapping("/api/users/{id}")
    UserResponse getById(@PathVariable Long id);
}
```

| 机制 | 工具 | 默认策略 |
|---|---|---|
| **超时** | Feign + Resilience4j | 连接 1s，读取 3s |
| **重试** | Resilience4j Retry | 最多 2 次，指数退避，仅幂等接口 |
| **熔断** | Resilience4j CircuitBreaker | 失败率 > 50% 触发，30s 半开 |
| **降级** | Fallback | 返回空 / 缓存 / 默认值，不抛异常 |
| **限流** | Resilience4j RateLimiter | 按业务 SLA 配置 |

**红线**：
- ❌ 不写超时（默认无限等待，雪崩源头）
- ❌ 重试非幂等接口（导致重复写入）
- ❌ Fallback 直接抛异常（破坏熔断器）

---

## 4. 可观测性（OpenTelemetry）

### 4.1 三件套

| 类型 | 工具 | 关键内容 |
|---|---|---|
| **Tracing** | OTel SDK + Jaeger/Tempo | 跨服务 traceId 透传，关键方法手动 span |
| **Metrics** | Micrometer + Prometheus | RED 指标（Rate/Errors/Duration）+ 业务指标 |
| **Logging** | SLF4J + Logback JSON | 必带 traceId、spanId、userId |

### 4.2 日志格式（JSON）

```json
{
  "ts": "2026-05-23T21:00:00.123Z",
  "level": "INFO",
  "logger": "BookingService",
  "msg": "booking created",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": 1001,
  "bookingId": 42,
  "roomId": 5
}
```

### 4.3 必埋指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `http.server.requests` | Histogram | RED 指标，按 uri/status 打 tag |
| `booking.create.total` | Counter | 业务成功数 |
| `booking.conflict.total` | Counter | 冲突次数（监控热点） |
| `notification.send.total` | Counter | 按 channel/status 打 tag |
| `db.connection.active` | Gauge | 连接池监控 |

**红线**：
- ❌ tag 高基数（如 userId 作 tag → 内存爆炸）
- ❌ 关键路径不埋点

---

## 5. 配置管理

### 5.1 分层

```
application.yml              # 通用
application-dev.yml          # 开发
application-test.yml         # 测试
application-prod.yml         # 生产
```

### 5.2 敏感配置

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}      # 环境变量注入
notification:
  wecom:
    secret: ${WECOM_SECRET}       # 配置中心 / Vault
```

**红线**：
- ❌ 任何密码/密钥/Token 出现在 git
- ❌ 生产配置写死在代码

---

## 6. 健康检查与生命周期

### 6.1 必备端点

| 端点 | 用途 |
|---|---|
| `/actuator/health/liveness` | K8s liveness：服务存活 |
| `/actuator/health/readiness` | K8s readiness：能否接流量 |
| `/actuator/prometheus` | 指标抓取 |
| `/actuator/info` | 版本/构建信息 |

### 6.2 优雅关闭

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

收到 SIGTERM 后：先停止接收新请求 → 等存量请求完成 → 关闭连接池 → 退出。

---

## 7. 异步任务

### 7.1 不要用 `@Async` 做关键业务

异步丢任务无补偿。关键异步任务（通知、级联取消）必须经消息队列。

### 7.2 推荐方案

| 场景 | 工具 |
|---|---|
| 通知发送（容错重试） | 本地表 outbox + 定时扫描 |
| 跨服务事件 | RocketMQ / Kafka |
| 定时任务 | XXL-Job / ShedLock（分布式锁防多实例重复） |

---

## 8. 测试金字塔

```
       /\
      /E2E\         少量：核心业务流（5-10 个）
     /------\
    /集成测试\      适量：Controller + DB + 关键依赖（30%）
   /----------\
  /  单元测试  \    主体：Service / Util（70%）
 /--------------\
```

- 单元测试：纯逻辑，Mock 所有依赖
- 集成测试：Testcontainers 启 MySQL/Redis，覆盖事务/SQL
- E2E：核心 happy path，CI 上跑

---

## 9. 安全基线

| # | 项 | 实现 |
|---|---|---|
| 1 | 认证 | JWT + 拦截器校验 |
| 2 | 授权 | `@PreAuthorize` 方法级 |
| 3 | 数据越权 | Service 内显式校验 `resource.ownerId == currentUser.id` |
| 4 | SQL 注入 | MyBatis `#{}` + 动态字段白名单 |
| 5 | XSS | 前端 React 默认转义；后端不存 HTML |
| 6 | CSRF | JWT 无状态，跨域控制好 origin |
| 7 | 限流 | 网关 + 接口级双层 |
| 8 | 审计 | 关键操作写 `audit_logs` 表 |

---

## 10. AI 涉及微服务任务的 Checklist

- [ ] 接口路径符合 REST 规范？
- [ ] HTTP 状态码语义正确？
- [ ] Feign 调用配齐了超时 / 重试 / 熔断 / 降级？
- [ ] 关键路径埋了 trace + metric + log？
- [ ] 日志带 traceId / userId？
- [ ] 敏感配置走环境变量？
- [ ] 健康检查端点正常？
- [ ] 是否有越权风险？
