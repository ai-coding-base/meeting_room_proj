# 会议室预订系统 v1.0 · 单元测试报告

| 项 | 值 |
|---|---|
| 报告日期 | 2026-05-24 |
| 报告范围 | C 组合：后端核心服务 + 前端核心组件 |
| 总用例数 | **67** |
| 通过 | **67** |
| 失败 | 0 |
| 跳过 | 0 |
| 总耗时 | < 2 s |

---

## 1. 总览

| 模块 | 框架 | 测试文件 | 用例 | 通过 | 失败 | 耗时 |
|------|------|---------|------|------|------|------|
| 后端 Java | JUnit 5 + Mockito + AssertJ | 4 | **40** | 40 | 0 | ~0.95 s |
| 前端 React/TS | Vitest 2 + RTL 16 + jsdom | 3 | **27** | 27 | 0 | 0.69 s |
| **合计** | — | **7** | **67** | **67** | **0** | **< 2 s** |

执行命令：
```bash
# 后端
cd meeting_room_proj/backend && mvn -DskipITs test

# 前端
cd meeting_room_proj/frontend && npm test
```

---

## 2. 后端测试详情（40 用例 / 100% 通过）

### 2.1 TokenServiceTest — 11 用例 / 0.108 s

`com.company.booking.security.TokenService` —— HMAC-SHA256 自签名 token 服务。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | issue + verify 往返 | userId / role / expiresAt 正确还原 |
| 2 | verify(null) | 返回 null |
| 3 | verify(空串/空白) | 返回 null |
| 4 | verify(垃圾字符串) | 返回 null |
| 5 | verify(无 `.` 分隔符) | 返回 null |
| 6 | 签名被篡改 | 常量时间比对失败，返回 null |
| 7 | payload 被篡改但签名固定 | 返回 null |
| 8 | 过期 token | expiresAt < now 返回 null |
| 9 | payload 字段数错误（少于 3 段） | 返回 null |
| 10 | 不同 secret 互不认 | 跨 service 校验失败 |
| 11 | 全 7 种 Role 签发 | 全部可签可验 |

### 2.2 AuthServiceImplTest — 5 用例 / 0.535 s

`com.company.booking.service.impl.AuthServiceImpl` —— 登录服务。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 用户不存在 | 抛 `UnauthorizedException`，统一提示，不调 tokenService/passwordEncoder |
| 2 | 用户被禁用 (isActive=false) | 抛 401，不校验密码 |
| 3 | 密码错误 | 抛 401，不签发 token |
| 4 | 登录成功 | 返回 token + UserInfo，调用 tokenService.issue |
| 5 | isActive=null | 视为激活，可登录（兼容老数据） |

> **安全保证**：用户不存在 / 密码错误 / 用户禁用三种情况返回**完全相同**的提示「用户名或密码错误」，防用户名枚举。

### 2.3 RoomServiceImplTest — 6 用例 / 0.209 s

`com.company.booking.service.impl.RoomServiceImpl` —— 会议室列表查询，含 JSON equipment 字段解析。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 合法 JSON equipment | 正确解析为 `List<String>`，所有字段映射正确 |
| 2 | equipment = null | 返回空列表，不抛异常 |
| 3 | equipment = "" / "   " | 返回空列表 |
| 4 | equipment 为非法 JSON | 不抛异常，返回空列表 + 日志 warn |
| 5 | DB 返回空列表 | 业务层返回空列表 |
| 6 | 多条记录 | 全部正确转换，VIP 标记保留 |

### 2.4 BookingServiceImplTest — 18 用例 / 0.094 s（5 嵌套类）

`com.company.booking.service.impl.BookingServiceImpl` —— 预订核心业务。

#### TimeValidation · 时间合法性 (5)

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 过去时间 | `InvalidTimeException` |
| 2 | 超过 7 天 | `InvalidTimeException` |
| 3 | 结束 ≤ 开始 | `InvalidTimeException` |
| 4 | 时长 < 15 分钟 | `InvalidTimeException` |
| 5 | 未对齐到 30 分钟 | `InvalidTimeException` |

#### RoomValidation · 会议室校验 (3)

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 会议室不存在 | `BizException` code=ROOM_NOT_FOUND |
| 2 | 状态为 MAINTENANCE | code=ROOM_NOT_AVAILABLE |
| 3 | 人数超出容量 | code=CAPACITY_EXCEEDED |

#### DelegationValidation · 代订授权 (5)

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | EMPLOYEE 代订他人 | `ForbiddenException` "当前角色无权代订" |
| 2 | ASSISTANT 无授权代订 | 查 delegation_grants → false → `ForbiddenException` |
| 3 | ASSISTANT 已授权代订 | 成功，bookerId=自己，principalId=被代订人 |
| 4 | ADMIN 代订任意人 | 直接放行，**不查授权表** |
| 5 | principalId 等于自己 | 视为自订，不查授权表 |

#### ApprovalRouting · 审批分流 (3)

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 普通会议室（≤50 人, 非 VIP） | status=CONFIRMED, needApproval=false |
| 2 | 大型会议室（capacity > 50） | status=PENDING, needApproval=true |
| 3 | VIP 会议室（即使容量小） | status=PENDING, needApproval=true |

#### SlotAndConflict · 时间槽与冲突 (2)

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 1 小时预订切分 | 生成 2 个 30 分钟槽，slot 首尾衔接 |
| 2 | DB 唯一约束冲突 | `DuplicateKeyException` → `BookingConflictException` "已被预订" |

---

## 3. 前端测试详情（27 用例 / 100% 通过）

### 3.1 api.test.ts — 9 用例 / 14 ms

`src/api.ts` —— axios 实例 + 拦截器 + 业务接口封装。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 请求拦截器 · 有 token | 自动注入 `Authorization: Bearer <token>` |
| 2 | 请求拦截器 · 无 token | 不注入 Authorization |
| 3 | 响应拦截器 · 401 | 清空 localStorage + 派发 `mr:unauthorized` 事件 |
| 4 | 响应拦截器 · 500 | 不清 session，不派发事件 |
| 5 | createBooking 成功 | 自动注入 `X-Request-Id` Header（客户端幂等） |
| 6 | createBooking 业务错误 | 抛 `[code] msg` 格式异常 |
| 7 | createBooking AxiosError | 透传 response.data 中的 code/msg |
| 8 | fetchRooms 正常 | 调 `/rooms` 解包返回数组 |
| 9 | login 成功 | 调 `/auth/login`，返回 token + user |

### 3.2 RoomList.test.tsx — 10 用例 / 77 ms

`src/RoomList.tsx` —— 会议室卡片选择器。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | 加载中 | 显示「加载会议室...」 |
| 2 | 加载失败 | 显示「加载会议室失败」+ 错误信息 |
| 3 | 空列表 | 显示「暂无可用会议室」 |
| 4 | 正常渲染 + 默认选中第一项 | 列出全部房间，触发 `onSelect(rooms[0])` |
| 5 | selectedId 已存在 | 不重复触发默认选中 |
| 6 | 点击卡片 | 触发 `onSelect(room)` |
| 7 | VIP 标签 | 仅 VIP 房间渲染「VIP」徽章 |
| 8 | 设备 chip 翻译 | 已知代码 → 中文+emoji；未知代码原样透传 |
| 9 | 选中态样式 | 被选中卡片有 `.selected` 类 |
| 10 | 卸载守卫 | 卸载后 promise resolve 不抛错（alive 守卫） |

### 3.3 BookingPage.test.tsx — 8 用例 / 166 ms

`src/BookingPage.tsx` —— 预订表单页。

| # | 用例 | 验证点 |
|---|------|-------|
| 1 | EMPLOYEE | 不显示代订人字段 |
| 2 | ASSISTANT | 显示代订人字段，文案「已授权用户」 |
| 3 | ADMIN | 显示代订人字段，文案「任意用户」 |
| 4 | 提交成功 | 显示「预订成功」+ 状态；时间字符串以 `:00` 结尾 |
| 5 | 提交失败 | 显示「失败」+ 错误信息 |
| 6 | 接口返回空 → 强制提交 | 显示「请先选择会议室」，不调 createBooking |
| 7 | ASSISTANT 填写代订人 | principalId 透传到 createBooking |
| 8 | ASSISTANT 留空代订人 | principalId = undefined（不传） |

---

## 4. 覆盖矩阵

| 业务点 | 后端覆盖 | 前端覆盖 |
|--------|---------|---------|
| 登录鉴权 | ✅ AuthServiceImplTest (5) + TokenServiceTest (11) | ✅ api.test 拦截器 (4) |
| 会议室列表 | ✅ RoomServiceImplTest (6) | ✅ RoomList.test (10) + api.test fetchRooms |
| 创建预订 | ✅ BookingServiceImplTest (18) | ✅ BookingPage.test (8) + api.test createBooking |
| 时间校验 | ✅ 时间 5 分支 | — (UI 层依赖 step=1800 + datetime-local) |
| 角色权限 | ✅ EMPLOYEE/ASSISTANT/ADMIN/有授权/无授权 | ✅ 三种角色字段差异化渲染 |
| 防超卖 | ✅ DuplicateKeyException → BookingConflictException | — |
| 审批分流 | ✅ 普通 / VIP / 大型三分支 | ✅ 提交成功后展示 status |
| 客户端幂等 | — | ✅ X-Request-Id 注入 |
| 401 自动跳登录 | — | ✅ 拦截器派发事件 + 清 session |

---

## 5. 测试质量保证

### 设计原则
- **表驱动 / 嵌套分组**：`BookingServiceImplTest` 用 `@Nested` 把 18 用例按业务维度切成 5 组
- **纯单元测试**：全部用 Mockito mock 数据访问层，无 DB / 无 Spring Context 启动
- **行为验证**：除断言返回值，关键路径用 `verify(mock, never())` 验证「不应发生」的副作用（不签发 token、不查授权表等）
- **常量时间比对**：TokenService 抗时序攻击测试用 `MessageDigest.isEqual`（被测代码已用）

### 边界覆盖（典型）
- null / 空串 / 空白字符串 / 非法格式 / 超长 / 越界
- 兼容旧数据：isActive=null
- 容量边界：≤50 vs >50（审批分流分水岭）
- 时长边界：< 15 分钟、未对齐 30 分钟整点
- 异步竞态：组件卸载后 promise 仍 resolve

### 已知警告（非阻塞）

| 文件 | 警告 | 原因 |
|------|------|------|
| RoomServiceImplTest.java（6 处）| Wrapper 泛型 unchecked | MyBatis-Plus `Wrappers.<T>lambdaQuery()` 在测试中用 `any(Wrapper.class)` 匹配，泛型擦除 |
| BookingServiceImplTest.java（2 处）| ArgumentCaptor.forClass(List.class) | 捕获 `List<TimeSlotEntity>` 时 List.class 无泛型 |

均为**类型擦除导致的编译期警告**，运行时无影响，测试已 100% 通过。如需消除可加 `@SuppressWarnings("unchecked")`，本次按 Surgical Changes 准则未做。

---

## 6. 修改清单

### 新增（7 个测试文件 + 1 个 setup）

```
backend/src/test/java/com/company/booking/security/TokenServiceTest.java
backend/src/test/java/com/company/booking/service/impl/AuthServiceImplTest.java
backend/src/test/java/com/company/booking/service/impl/RoomServiceImplTest.java
backend/src/test/java/com/company/booking/service/impl/BookingServiceImplTest.java

frontend/src/__tests__/api.test.ts
frontend/src/__tests__/RoomList.test.tsx
frontend/src/__tests__/BookingPage.test.tsx
frontend/src/test/setup.ts
```

### 修改（仅 3 个前端配置文件）

| 文件 | 改动 |
|------|------|
| `frontend/package.json` | 加 `test` / `test:watch` 脚本；devDependencies 加 vitest/RTL/jsdom |
| `frontend/vite.config.ts` | 加 `test` 配置（jsdom + globals + setupFiles）|
| `frontend/tsconfig.json` | `types` 加 `vitest/globals` 与 `@testing-library/jest-dom` |

### 业务代码改动

**零** — 全程未修改任何 `src/main` 下的源码。

---

## 7. 后续建议

按价值排序，本次未做：

1. **Controller 层 SpringMvcTest**：用 `@WebMvcTest` 测 `BookingController` / `AuthController` / `RoomController` 的 HTTP 401/403/400/200 行为
2. **集成测试 (`@SpringBootTest` + Testcontainers MySQL)**：验证 `time_slots` 唯一约束在真实 DB 的并发表现
3. **E2E**：Playwright 跑登录 → 选房 → 下单 → 看结果整条链路
4. **覆盖率报告**：JaCoCo（Java）+ vitest --coverage（前端）出 HTML 报告

---

**报告人**：CodeBuddy AI
**生效命令**：`mvn test` / `npm test`
**全部用例可重复执行，结果稳定**
