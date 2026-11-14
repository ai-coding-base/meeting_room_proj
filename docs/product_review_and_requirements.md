# 会议室预订系统 v1.0 — 产品与架构审视报告暨需求增补建议

> 版本：v1.0
> 日期：2026-11-14
> 视角：资深产品经理 + 资深系统架构师
> 依据：`prd.md`、`requirement_review.md`、`schema.md` / `schema.sql`、`docs/api.md`、`docs/UNIT_TEST_REPORT.md`、`docs/TEST_REVIEW_BASELINE.md`、`CLAUDE.md`、`.codebuddy/rules/*`、`backend/src/**`（55 个 Java 文件）、`frontend/src/**`（12 个文件）
> 输出位置：`docs/product_review_and_requirements.md`

---

## 0. 口径与范围说明

### 0.1 需求数量口径

原始诉求同时出现「新增 15 个非常有必要的产品需求」与「仅从改进产品本身提出 10 个需求」，本报告按**可同时满足两者的口径**执行：

| 类别 | 数量 | 编号 | 说明 |
|---|---|---|---|
| 纯产品改进需求 | **10** | `PR-01` ~ `PR-10` | 全部只依赖本系统自身，补齐产品闭环与可用体验 |
| 由缺陷转化的优化需求 | **5** | `OPT-01` ~ `OPT-05` | 由第 5 章 Bug 归并转化，修复既有实现的错误行为 |
| **新增需求合计** | **15** | — | 10 + 5 |
| Bug 清单 | 12 条 | `BUG-01` ~ `BUG-12` | 单独列出，用于追溯，**不重复计入 15 项** |

### 0.2 明确排除的范围（不依赖第三方）

按诉求，以下**一律不作为需求提出**，也不写进任何验收标准：

- 短信 / 邮件 / 企业微信三通道推送与到达率、企微/邮件「一键审批」回调、会前提醒推送；
- SSO / AD / OAuth2 / HR 组织架构同步；
- Outlook / 飞书 / Google 日历同步；
- 会议室设备 IoT 联动、自动开关、门禁刷卡签到、「未出席」识别；
- BI / 数据仓库外推、跨系统报表；
- 移动端 App、其他项目（`docs/training-digitalization-solution.md` 属另一「OA 培训签名」项目，整体排除）。

「站内消息中心 / 站内待办 / 站内看板 / 站内导出」属于本系统自身能力，不受该约束，但本报告 10 项产品需求中只纳入与核心闭环直接相关的部分，站内推送中心列为后续备选，见 §8.3。

### 0.3 评估边界：Bug vs 产品需求

| 判定 | 定义 | 归属 |
|---|---|---|
| **Bug** | 已实现的代码产生了错误、不安全或与契约不符的行为 | 第 5 章 → 第 7 章 `OPT-*` |
| **产品需求** | PRD 已承诺、但代码里完全没有实现的用户可感知能力 | 第 6 章 `PR-*` |

例：「没有取消页面」是产品需求 `PR-02`；「已经创建成功的预订永不释放 `time_slots`，导致时段永久锁死」是 Bug `BUG-02`。二者相关但不重复计数。

### 0.4 一个必须先说清的语境

`docs/TEST_REVIEW_BASELINE.md:1-3` 自述为「培训演示 Act 2 的预跑标准答案」，`:134-144` 甚至建议「故意埋一条 AC 制造 ❌ 效果」；`docs/UNIT_TEST_REPORT.md` 又宣称 67 用例 100% 通过。也就是说，本项目**同时承担「AI 协作培训样例」与「宣称 v1.0 可交付的产品」两种身份**。这直接解释了为什么会出现「骨架完整、闭环缺失」「文档互相矛盾」的现象。

> 产品判断：若定位是教学样例，当前缺口是合理的教学设计，本报告的需求应作为「进阶练习题库」；若定位是要上线的产品，则 `PR-01`~`PR-03` 与 `OPT-01`~`OPT-03` 属于发布阻塞项。此项定位需业务方确认（见 §9 第 1 维度）。

### 0.5 证据标注约定

下文 `文件:行号` 均为仓库根目录相对路径 + 源码实际行号，可直接跳转核对。凡标注「待验证」的结论，表示需要真实运行环境/集成测试才能定级，本报告不把它当作已确认事实。

---

## 1. 执行摘要

**一句话结论**：这是一套「工程骨架优秀、产品闭环缺失」的系统——后端分层、防超卖、统一异常、鉴权链做得相当扎实，但用户从登录到「预订成功」之后**什么事都做不了**：看不到自己的预订、不能取消、不能审批、不能改期，且每一次成功预订都会把一个 30 分钟槽位永久锁死。

**三个最关键风险**：

1. **安全 P0**：`TokenService.java:32` 内置默认签名密钥，生产环境可直接伪造任意 `ADMIN` 身份（`BUG-01`）。
2. **可用性 P0**：`time_slots` 只写不删 + `PENDING` 永不流转，会议室时段单调耗尽，系统上线数天即不可用（`BUG-02`）。
3. **越权 P1**：幂等键以客户端 `X-Request-Id` 为全局主键且重放时不校验用户，A 的预订详情可被 B 读取（`BUG-03`）。

**建议顺序**：先修 `OPT-01` / `OPT-02` / `OPT-03`（安全与数据正确性，发布阻塞）→ 再补 `PR-01` / `PR-02` / `PR-04`（用户最小可用闭环）→ 再补 `PR-03` / `PR-05`~`PR-10` 与 `OPT-04` / `OPT-05`。

**现况对账**（`docs/api.md:75-87` 定义了 7 个端点，实际只实现 3 类）：

| 能力 | PRD 宣称 | 代码现状 | 证据 |
|---|---|---|---|
| 创建预订 | ✅ US-02 | ✅ 已实现（含幂等占位、防超卖、审批分流） | `BookingController.java:42-48`、`BookingServiceImpl.java:57-108` |
| 列出/筛选会议室 | ✅ US-01 | ⚠️ 仅全量按状态返回，无筛选、无时段可用性 | `RoomController.java:32-35`、`RoomServiceImpl.java:32-41` |
| 登录 / 当前用户 | — | ✅ 已实现 | `AuthController.java:32-47` |
| 登出 | — | ⚠️ 空实现 | `AuthController.java:53-56` |
| 我的预订 / 详情 | ✅ 端点 #4/#5 | ❌ 无接口、无页面 | `docs/api.md:82-83` |
| 取消预订 | ✅ US-05 / 端点 #6 | ❌ 全库无 `DELETE` / `delete` 代码 | `docs/api.md:84` |
| 审批通过/驳回 | ✅ US-04 / 端点 #7 | ❌ `PENDING`/`REJECTED` 仅存在于枚举与实体 | `BookingStatus.java`、`BookingEntity.java` |
| 通知 / 审计 / 看板 | ✅ US-06 / US-07 | ❌ `notifications`、`audit_logs` 两表 Java 零引用 | `schema.sql:156-192` |
| 会议室管理后台 | ✅ US-07 | ❌ 无 CRUD、无状态流转 | 全库检索无更新接口 |

> `docs/TEST_REVIEW_BASELINE.md:110-116` 的量化结论：44 条 AC 中 ✅17（39%）、⚠️4（9%）、❌23（52%），High 缺口 14 条，集中在取消、审批、后台运营、跨部门权限、隐私可见性。

---

## 2. 项目现状地图

| 层 | 规模 / 结构 | 关键事实 |
|---|---|---|
| 产品文档 | `prd.md`（7 个 US、44 条 AC）、`requirement_review.md`（5 维度决策）、`schema.md`、`docs/api.md` | 决策链完整，但与实现脱节；`README.md` 为 0 字节 |
| 后端 | 55 个 Java 文件，4 层（controller / service+impl / mapper / domain+DTO / common+security+config），3 个 yml，1 个 mapper XML | 仅 2 个业务接口 + 3 个鉴权接口 |
| 前端 | 12 个文件，全部平铺在 `frontend/src/`，Vite + React 18 + TS strict | 无路由、无状态库、无页面分层；仅「登录 + 预订表单」两屏 |
| 数据 | 9 张表（`schema.sql`），软删除 `deleted_at` 全覆盖 | 其中 `notifications`、`audit_logs`、`departments` 在 Java 侧零引用 |
| 测试 | 后端 4 个 Service/Token 测试类 + 前端 3 个测试文件 | 全为 Mockito / RTL 单测，**零** `@SpringBootTest` / MockMvc / 真实 DB / 并发测试 |
| 工程化 | 根目录 `.gitignore`（本次审阅前已补）、`package-lock.json`、`tsconfig.json` strict | 无 CI、无 lint 门禁、无覆盖率门禁 |

---

## 3. 做得好的地方

### 3.1 产品与文档设计

| # | 优点 | 证据与价值 |
|---|---|---|
| 1 | **目标可量化**，不是「做一个预订系统」 | `prd.md:13-20`：利用率 ≥60%、冲突报错率 <0.1%、通知到达率 ≥99%、预订平均耗时 <30s。指标可直接驱动验收 |
| 2 | **决策链可追溯** | `prd.md:5` 声明依据 `requirement_review.md` 第 6/7 章；`docs/api.md:99` 每条接口标注关联 US；`CLAUDE.md:103-105` 要求需求→设计→代码可追溯 |
| 3 | **业务克制感强** | `prd.md:117` 与 `requirement_review.md §7.5`：>4h / >8h / 7 日 >20h 是「只提醒不阻断」的软约束，而不是拍脑袋的硬限额，符合内部工具降低摩擦的产品取向 |
| 4 | **隐私意识** | `prd.md:57,119,177` 默认 `PUBLIC` 但可切 `PRIVATE`、主题按可见性脱敏；`docs/api.md:217-224` 对 PRIVATE 做了字段级约定 |
| 5 | **范围边界明确** | `prd.md:182-192` 显式列出 In scope / Out of scope（SSO、日历、IoT、BI、未出席、移动端），避免范围蔓延 |
| 6 | **用 AC 量化业务覆盖率** | `docs/TEST_REVIEW_BASELINE.md:17-116` 用 44 条 AC 做四方对账，把「测了多少代码」转成「覆盖了多少业务规则」，方法论值得保留 |
| 7 | **并发方案选型务实** | `requirement_review.md §6.2` 明确不引入分布式锁，用 DB 唯一约束兜底 + READ COMMITTED，符合单体系统的复杂度预算 |

### 3.2 后端架构

| # | 优点 | 证据与价值 |
|---|---|---|
| 1 | **分层清晰、依赖方向正确** | Controller 只做校验与编排（`BookingController.java:42-48`），Service 承担事务与业务规则（`BookingServiceImpl.java:57-63`），Mapper 只做 SQL；构造器注入（`@RequiredArgsConstructor`）无字段注入 |
| 2 | **防超卖设计正确** | `BookingServiceImpl.java:94-102` 批量插槽 + `DuplicateKeyException` → `BookingConflictException`；`schema.sql:103` `uk_room_slot(room_id, slot_start)` 兜底并发；`@Transactional(rollbackFor = Exception.class)`（`:58`）保证 booking 与 slots 原子 |
| 3 | **统一契约与语义化错误码** | `common/result/R.java` + `GlobalExceptionHandler.java:25-105` 把 401 / 403 / 409 / 400 / 404 / 500 与业务码一一映射，前端可稳定解包 |
| 4 | **鉴权链路完整且有安全意识** | `WebMvcConfig.java:29-34` 白名单拦截；`AuthInterceptor.java:38-43` **每请求回查 DB 用户与角色**（忽略 token 内 role），角色变更后旧 token 不会提权；`TokenService.java:74` 常量时间比对；`AuthServiceImpl.java:31` 登录失败统一提示防用户名枚举；BCrypt 且响应不含 `password_hash` |
| 5 | **幂等设计有整体构思** | `IdempotencyService` 以 `requestId + userId + endpoint` 为契约（`IdempotencyServiceImpl.java:38-39`），客户端注入 `X-Request-Id`（`frontend/src/api.ts:120-127`），TTL 24h（`schema.sql:150`）——思路对，实现有偏差（见 `BUG-03`） |
| 6 | **领域细节到位** | `@EnumValue`、`@TableField("is_vip")`、`MetaObjectHandler` 自动填充 `created_at/updated_at`、`@TableLogic` 软删除、Bean Validation 注解齐全 |
| 7 | **单测质量在核心规则上较高** | `TokenServiceTest` 覆盖签发 / 篡改 / 过期 / 跨密钥 / 全角色；`BookingServiceImplTest` 覆盖时长、7 天、容量、VIP 分流、代订授权等分支 |

### 3.3 前端与工程

| # | 优点 | 证据 |
|---|---|---|
| 1 | API 层统一封装 | `api.ts:58-82` axios 实例 + Bearer 注入 + 401 集中清理与事件解耦；`:85-98` `unwrap` 统一解包 `R<T>` |
| 2 | 幂等头主动对齐后端契约 | `api.ts:120-127` 每次创建带 `X-Request-Id` |
| 3 | 交互防重复与异步安全 | `Login.tsx:56`、`BookingPage.tsx:155` 提交期禁用按钮 + `try/finally` 恢复；`RoomList.tsx:24-41` `alive` 守卫防卸载后 setState |
| 4 | 状态三态齐全、降级友好 | `RoomList.tsx:43-52` 加载 / 错误 / 空态；`:5-11,79` 设备码映射含未知码兜底；`RoomServiceImpl.java:60-69` equipment JSON 解析失败不阻断列表 |
| 5 | 工程基线 | `tsconfig.json` strict、`main.tsx` StrictMode、`index.html` `lang`、`Login.tsx:41,51` autoComplete、根目录 `.gitignore` 覆盖 Maven/Node/密钥/IDE 产物 |
| 6 | 登出容错 | `App.tsx:17-25` 后端 logout 失败也清本地状态，不让用户卡在「退不出去」 |

---

## 4. 做得不好的地方

### 4.1 产品层面

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 1 | **闭环断裂：宣称完整闭环，实际只有「创建」** | `prd.md:11,184` 宣称覆盖浏览→预订→审批→取消→通知→后台运营；`docs/api.md:87` 承认「v1.0 优先实现 #3 创建预订」；`docs/TEST_REVIEW_BASELINE.md:11-13` 直指审批/取消/通知/后台「发布即裸奔」 | 用户订完只看到一屏 JSON（`BookingPage.tsx:160-166`），刷新即失；无法查、改、取消。核心用户价值不成立 |
| 2 | **用户旅程缺「预订后的管理」** | 无「我的预订」页面/接口；`BookingDetailResponse` 只在创建瞬间返回 | 无法回答「我明天在哪开会」「我的 VIP 申请批了吗」这两个最高频问题 |
| 3 | **US-01 的浏览体验完全没做** | `prd.md:38-44` 要求按日期+时段+容量+设备+区域筛选、时间轴、灰显占用；实际 `RoomServiceImpl.java:32-41` 只按 `status` 全量返回 | 用户盲选时段，只有提交后才收到 409；`<30 秒完成预订` 指标不可能达成 |
| 4 | **角色模型不一致、权限矩阵缺失** | `prd.md:26-32` 定义 5 类业务角色，`schema.sql:24` 定义 7 种技术角色；`RECEPTIONIST` / `AUDITOR` 权限全文档未定义；无一张「角色 × 操作」矩阵 | 后台、审计、前台相关需求无法验收；`superior_id` 手工配置且 `seed.sql:9-17` 全为 NULL，「上级取消下级」无数据基础 |
| 5 | **业务规则自相矛盾** | `requirement_review.md:114`「允许任意起止、不强制对齐」vs `docs/api.md:128`「startTime 对齐 30 分钟槽」（而 `:129` 对 endTime 只要求 > start / ≥15min）；`prd.md:51` 最短 15min 与 30min 槽位共存；`schema.md:109-113` 允许 `CANCELLED → COMPLETED`。另 `requirement_review.md:122` 自己已标注「不限最长时长 + 不限配额 = 单人可锁死一整周」，但实现未做任何防护 | 开发无法判定正确行为，测试无法写断言（也直接导致 `BUG-04`）；文档已识别的风险没落到代码 |
| 6 | **状态机定义不完整** | `PENDING` 是否占槽、能否取消；`REJECTED` 后能否重订；审批人如何确定（`schema.sql:123` `approver_id` 赋值规则缺失）；`DECOMMISSIONED` 是否可逆；`COMPLETED` 由谁在何时流转——全部未定义 | 直接导致 `BUG-02` 的死锁 |
| 7 | **把依赖第三方的能力写成 v1.0 In scope 指标** | `prd.md:19,89-97,184` 通知三通道与到达率 ≥99%，但 `notifications` 表 Java 零引用 | 上线口径失真；评审时无法判断「通知」到底算不算交付物 |
| 8 | **交付物噪音** | `README.md` 0 字节；`docs/training-digitalization-solution.md` 是另一个项目；`CLAUDE.md:116-120,199` 把 `prd.md` 等写在 `docs/` 下（实际在根目录）；`docs/api.md:224` 引用不存在的 `prd.md §4.1`；`schema.md:126` 引用不存在的「5.3 三通道」 | 新人无法上手，交叉引用失效，评审成本高 |
| 9 | **演示数据/口令混入产品交付物** | `seed.sql:3` 明文写统一弱口令 `password123`；`Login.tsx:10-11,68-76` 预填并公示演示账号；`seed_rooms.sql:3` 注释称「已存在的 3 间」但 `seed.sql:26-28` 只插 2 间，`UPDATE ... WHERE id=3`（`seed_rooms.sql:12-14`）匹配 0 行 | 生产同源部署可一键进他人账号；演示环境数据与文档不符 |

### 4.2 架构层面

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 1 | **「骨架完整、器官缺位」** | 9 张表、枚举、DTO、异常类一应俱全，但 Service 只有 create / list / login | 架构评审容易给出高分，实际用户价值接近零；后续补功能时才发现状态机、槽位释放等基础设施没设计 |
| 2 | **时段账本只增不减，无定时任务** | `TimeSlotMapper.java:26` 仅 `insertBatch`；全库无 `delete` / `@EnableScheduling`；`schema.sql:106` 注释「释放即物理删除」但无代码 | 系统资源单调耗尽（P0） |
| 3 | **幂等契约与实现不一致** | 接口声明三元组（`IdempotencyServiceImpl.java:38`），表主键只有 `request_id`（`schema.sql:151`），重放只按 `requestId` 查（`:74-77`） | 越权读他人预订、请求被吞（P1） |
| 4 | **Token 生命周期无法收敛** | `TokenService.java:28` 固定 8h、`AuthController.java:53-56` logout 空实现、无 refresh / 黑名单 | 泄露后 8 小时内无法吊销；「退出」只是前端假象 |
| 5 | **配置与部署耦合错误** | `application.yml:5` 硬编码 `profiles.active: dev`；`:7` `jackson.date-format` 对 `LocalDateTime` 不生效；`application-prod.yml:14` `${DB_PASSWORD}` 无默认值、`:13` 用 root 连生产库；`:11` 明文内网地址 | prod 未显式覆盖就连本地库；权限过大；内外网信息随仓库外泄 |
| 6 | **逻辑删除与唯一索引语义存疑** | `application.yml:22-24` `logic-not-delete-value: "null"` / `logic-delete-value: "now()"`（**待验证**：MP 3.5.x 是否转成 `IS NULL` / `NOW()`）；`schema.sql:62,83` 唯一键含 `deleted_at`，MySQL 唯一索引中 NULL 不参与比较 | 若字面拼接则登录/列表全空（潜在 P0）；可插入重复有效授权与同名会议室 |
| 7 | **零集成测试** | 后端 4 个测试类全 Mockito，`BookingServiceImplTest.java:50-51` 明言不关心事务/幂等真实实现；`IdempotencyServiceImpl` / `AuthInterceptor` / `WebMvcConfig` / `GlobalExceptionHandler` 零测试 | 事务边界、唯一约束、401 白名单、异常映射、mapper XML 全部漏测；单测 100% 通过给出虚假安全感 |
| 8 | **可观测性与防护缺失** | `R.traceId` 恒 null；无分页、无限流、无 CORS 配置、无审计写入 | 100 QPS 目标（`prd.md:175`）无验证手段；审计留痕 ≥1 年（`prd.md:177`）无法落地 |

### 4.3 前端与工程交付层面

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 1 | **实现与自定前端规范不符** | `.codebuddy/rules/react-frontend.md` 要求 `pages/`、`api/`、`hooks/`、`stores/` 分层 + TanStack Query + RHF/Zod + AntD；实际 12 个文件全平铺在 `src/`，无路由、无服务端状态管理，用 `useState` 装服务端数据 | 补 10 个产品需求时必然推倒重来，技术债要在补功能前先还 |
| 2 | **客户端「信任本地缓存」的安全模型** | `App.tsx:8,27-29` 只凭 `getUser()` 判登录；`auth.ts:42-44` `isLoggedIn()` 与 `api.ts:106-108` `fetchMe()` 均为死代码 | 伪造 `mr_user` 即可看到主界面与「管理员」标签（后端仍会 401，属状态欺骗但体验混乱） |
| 3 | **表单零业务校验、错误不可读** | `BookingPage.tsx:41-66,82-88,109-153` 不校验结束时间、时长、7 天、容量、人数；`api.ts:87-96` 原样抛 `[BOOKING_CONFLICT]` / `Network Error` / `timeout of 10000ms exceeded` | 用户只能靠 409 试错，失败后不知道怎么改 |
| 4 | **成功后状态不收敛** | `BookingPage.tsx:47-66,160-166` 结果常驻、表单不重置、房间列表不刷新；`RoomList.tsx:23-41` 仅首次加载且无重试入口 | 改主题再提交即 409；列表加载失败后只能刷新整页 |
| 5 | **测试覆盖盲区恰好盖住关键缺陷** | `frontend/src/test/setup.ts` 把 `randomUUID` 固定为常量，掩盖 `BUG-07`；无 `App` / `Login` / `auth` 测试 | 登录态恢复、token 失效、401 切页、重复提交全部未测 |
| 6 | **无 CI / lint / 覆盖率门禁** | 仓库无流水线配置；`backend/target`、`frontend/dist`、`frontend/tsconfig.tsbuildinfo` 曾为未忽略产物（现已由 `.gitignore` 覆盖） | 无法阻止带 P0 缺陷的版本被推送 |

---

## 5. Bug 清单（12 条，含复现、根因、改进方案与转化映射）

> 严重度：P0 = 安全/核心可用性，发布阻塞；P1 = 主要功能错误或越权；P2 = 体验、数据完整性或工程缺陷。

### BUG-01 [P0] 生产使用内置默认签名密钥，可伪造任意身份

- **位置**：`backend/src/main/java/com/company/booking/security/TokenService.java:32`、`:40-42`；`application-dev.yml` / `application-prod.yml` / `application.yml` 均未配置 `app.auth.secret`。
- **复现**：不注入 `APP_AUTH_SECRET` 直接以 prod 启动 → `init()` 只打一条 warn 不阻断 → 攻击者按公开的 token 格式 `userId|ROLE|expiresAtEpochMs`（`:48-52`）用源码里的默认密钥算 HMAC → `AuthInterceptor.java:34` 验签通过 → 以任意 `userId` + `ADMIN` 身份调用全部接口。
- **实际 vs 期望**：实际「任何人都可签发管理员 token」；期望「密钥只来自环境变量，缺失或强度不足即启动失败」。
- **根因**：把「开发便利」写进了生产默认值，且告警未升级为硬失败。
- **影响**：全量数据泄露、任意代订、任意强制取消（若后续实现），合规事故。
- **改进方案**：移除默认值改 `${APP_AUTH_SECRET}`；启动时校验长度 ≥32 且不等于已知默认值，不满足直接 `fail-fast`；dev 用独立显式配置；补「默认密钥启动失败」的集成测试。
- **转化**：`OPT-01`

### BUG-02 [P0] 预订永不释放 `time_slots`，`PENDING` 永不流转，时段永久锁死

- **位置**：`TimeSlotMapper.java:26` 仅 `insertBatch`；`BookingServiceImpl.java:94-102` 只写槽；`schema.sql:103,106`（唯一约束 + 「释放即物理删除」注释）；全库无 `DELETE` / `delete` / `@EnableScheduling`；`BookingStatus` 的 `PENDING`/`REJECTED` 无流转代码。
- **复现**：创建任意预订（VIP/大会议室会进 `PENDING`，`BookingServiceImpl.java:87-92`）→ 会议结束、审批驳回、用户取消都没有任何代码删槽 → `uk_room_slot` 使该会议室该 30 分钟槽位此后永远返回 409。
- **实际 vs 期望**：实际「槽位占用单调增长，含未批准/已结束的预订」；期望「CONFIRMED 结束 / CANCELLED / REJECTED / PENDING 超时 → 释放槽位，可被重新预订」。
- **根因**：只实现了「占用」这一半状态机，取消/审批/定时任务三条释放路径均缺失；`uk_room_slot` 不区分 `bookings.status`。
- **影响**：会议室可用时段随时间归零，是上线后数天内必然爆发的可用性事故。
- **改进方案**：定义唯一权威的「槽位账本」写入/释放规则；取消、驳回、维护级联、PENDING 超时统一在同一事务内 `deleteByBookingId`；补定时任务做状态推进与过期释放；唯一键语义改为「有效占用」口径；补并发与真实 DB 的集成测试。
- **转化**：`OPT-02`（释放流程的产品入口由 `PR-02` / `PR-03` 提供）

### BUG-03 [P1] 幂等键跨用户复用 → 越权读取他人预订详情 / 请求被吞

- **位置**：`IdempotencyServiceImpl.java:55-58`、`:74-77`；`schema.sql:151`（PK 仅 `request_id`）；`BookingController.java:44`（`X-Request-Id` 由客户端提供）。
- **复现**：用户 A 用 `X-Request-Id: R1` 创建成功；用户 B 用同一个 `R1` 提交 → INSERT 冲突进入「重放」分支 → `loadResponse` 不比对 `userId` / `endpoint` / `expires_at`，直接反序列化返回 A 的 `BookingDetailResponse`（含 `bookerId`、`principalId`、`subject`、会议室与时段）。反向亦可：B 抢注某 ID 会让 A 的真实创建被当作重放而「不落库」。
- **实际 vs 期望**：实际「幂等键是全局命名空间，重放即返回他人数据」；期望「重放前校验 `userId` + `endpoint`，不一致返回 409/400，绝不返回他人响应」。
- **根因**：接口契约是三元组、DB 主键是单列、查询只用单列；TTL 字段从不参与查询。
- **影响**：可枚举/猜测 requestId 造成信息泄露，且能拒绝服务（抢注）。
- **改进方案**：主键改 `(request_id, user_id, endpoint)` 或在重放路径显式比对三者 + `expires_at`；`X-Request-Id` 服务端限长 ≤64（当前超长会撞 `schema.sql:144 VARCHAR(64)` 变 500）；并发重放加状态机（PROCESSING / DONE），处理中返回 409 而非半成品。
- **转化**：`OPT-03`

### BUG-04 [P1] 结束时间未校验 30 分钟对齐与上限，末槽越界、超额占用

- **位置**：`BookingServiceImpl.java:156`（只校验 start）、`:196-205`（`while(cursor < end)` 固定 +30min）、`:146`（只限制 start ≤ now+7d）、`:46`（允许 15min 时长）。
- **复现**：提交 `09:00–09:45`（合法：时长 45min ≥15min，start 对齐）→ 生成 `09:00–09:30`、`09:30–10:00` 两槽，实际占用到 10:00；他人订 `09:45–10:00` 被 409 拒绝，且末槽 `slot_end` 超过 `booking.end_time`，与 `schema.sql:98` 语义冲突。另：start 在 7 天内、end 填 +30 天可生成 1440 个槽位，撑爆单条批量 INSERT。
- **实际 vs 期望**：实际「多占最多 29 分钟 / 可无限占用未来」；期望「起止均对齐 30 分钟、时长为 30 的整数倍或有明确的末槽规则，且 end 受最长时长与 7 天窗口约束」。
- **根因**：`prd.md:51`「最短 15min」与「30min 槽位」两条规则未做产品决策，实现只校验了 start；`docs/api.md:128-129` 也只约束 startTime 对齐、对 endTime 无上限要求，`requirement_review.md:114,122` 更明确「允许任意起止、不限最长时长」并自认存在「单人锁死一整周」风险。
- **影响**：虚占资源、误报冲突、潜在的批量插入性能问题；也让「冲突报错率 <0.1%」指标失真。
- **改进方案**：先由业务确认 15min 与 30min 槽位的取舍（见 §9 维度 1），再在 `validateTimeRange` 补 end 对齐、时长上限、end ≤ now+7d；`buildSlots` 按确认后的规则生成，禁止末槽越界；补 09:00–09:15 / 09:00–09:45 / 跨天边界用例。
- **转化**：`OPT-02`

### BUG-05 [P1] 部门专属会议室与代订对象均无权限校验

- **位置**：`schema.sql:77`（`department_id` 非空 = 部门专属）、`prd.md:120`；`RoomServiceImpl.java:32-41` 与 `RoomController.java:30-34`（注释自认「保留入参以便后续按部门权限过滤；当前未使用」）；`CurrentUser.java:16` 无 `departmentId`；`BookingServiceImpl.java:161-174` 不比较部门；`:121-135` 不校验 `principalId` 是否为真实有效用户。
- **复现**：任意部门员工 `GET /api/rooms` 看到全部部门专属会议室并可直接预订成功；代订时传入任意数字 `principalId`（ASSISTANT 走授权表、ADMIN 直接放行）也能落库，产生指向不存在用户的脏数据。
- **实际 vs 期望**：实际「专属规则只在文档里」；期望「列表按 `user.department_id` 过滤，提交时二次校验部门；`principalId` 必须是有效且在职的用户」。
- **根因**：权限维度在 `CurrentUser` 建模时遗漏，且 DTO/响应里都不带 `departmentId`。
- **影响**：跨部门资源被占用；脏数据让「我的预订」「通知对象」等后续需求无法正确实现。
- **改进方案**：`CurrentUser` 补 `departmentId`；列表查询与创建校验双端过滤；`RoomListItem` 暴露部门标签；`principalId` 查 `users` 校验 `is_active`；PRIVATE 可见性按 `docs/api.md:217-224` 落地。
- **转化**：`OPT-04`（可见性筛选的产品形态见 `PR-05`）

### BUG-06 [P1] 幂等响应写失败被吞 + `catch` 范围过宽

- **位置**：`IdempotencyServiceImpl.java:62-71`、`:49-59`；`GlobalExceptionHandler.java:99-105`。
- **复现**：`persistResponse` 序列化/更新失败时只 `log.error`，外层 `BookingServiceImpl.java:58` 的事务照常提交 → booking 已创建但 `idempotency_keys.response = NULL`；此后任何重放命中 `:78-79` 抛 `IllegalStateException` → 全局兜底返回 500。另 `:55` 的 `catch (DuplicateKeyException)` 把 `:52 action.get()` 也包在里面，业务侧任何未包装的 DuplicateKey 都会被误判为「重放」。
- **实际 vs 期望**：实际「数据已生效但客户端认为失败，且永久无法重放」；期望「响应快照写入失败则整体回滚，或独立事务记录且与业务异常隔离」。
- **根因**：异常被降级为日志，事务边界与幂等状态机未分开；catch 粒度覆盖业务执行。
- **改进方案**：`persistResponse` 失败抛异常触发回滚；把「占位 INSERT」单独 try/catch，`action.get()` 移到 catch 之外；为占位记录加 PROCESSING/DONE 状态；补「序列化失败回滚」测试。
- **转化**：`OPT-03`

### BUG-07 [P1] 前端每次提交重新生成 `X-Request-Id`，用户重试不幂等

- **位置**：`frontend/src/api.ts:121-127`（`crypto.randomUUID()` 在 `createBooking` 内部）、`frontend/src/BookingPage.tsx:47-66`；对照 `BookingController.java:44`、`IdempotencyServiceImpl.java:49-59`。
- **复现**：首次提交因网络超时/响应丢失未收到结果 → 用户再点一次 → 生成新 UUID → 后端视为全新请求 → 若首次已落库，则命中 `uk_room_slot` 返回 409「该时段已被预订」，用户误以为失败并反复重试。
- **实际 vs 期望**：实际「幂等只在服务端单请求内有效，用户级重试无效」；期望「同一表单内容在成功或显式放弃前复用同一 requestId」。
- **根因**：幂等键生成位置放在 API 函数而非表单会话；`test/setup.ts` 固定 `randomUUID` 恰好掩盖该问题。
- **影响**：重试产生误导性冲突，是「冲突报错率 <0.1%」的主要来源之一。
- **改进方案**：在表单挂载时生成并持有 `requestId`，提交期间与失败重试复用；成功后轮换；显式修改表单关键字段时轮换；补重复点击/超时重试的前端测试。
- **转化**：`OPT-03`

### BUG-08 [P1] 仅凭 localStorage 判登录态，角色可伪造，`fetchMe` / `isLoggedIn` 是死代码

- **位置**：`frontend/src/App.tsx:8,27-29`、`frontend/src/auth.ts:32-35,42-44`、`frontend/src/api.ts:106-108`、`frontend/src/BookingPage.tsx:38-39,91-107`。
- **复现**：删除 `mr_token` 但保留 `mr_user` 并刷新 → 直接渲染顶栏与预订页（只有首个 API 返回 401 才被踢回，网络失败则长期「假登录」）；手写 `mr_user={"role":"ADMIN",...}` 刷新 → 显示「管理员」标签与代订人输入框。后端 `AuthInterceptor.java:28-44` 仍会 401，故属客户端状态欺骗而非服务端越权。
- **实际 vs 期望**：实际「可被篡改的本地缓存是 UI 凭据」；期望「mount 时以 `/auth/me` 校验 token，并以服务端返回的角色为准，失败清 session，校验期间显示加载态」。
- **根因**：登录态恢复流程没接上已实现的 `/auth/me`（`AuthController.java:38-47`）。
- **改进方案**：App 增 `bootstrapping` 态；有 token 才调 `fetchMe()`，成功用返回值覆盖本地 user，失败 `clearSession()`；401 事件与登录失败区分处理；补登录态恢复测试。
- **转化**：`OPT-01`

### BUG-09 [P2] 请求字段接收但不落库，`attendees` 与 `booking_id` 恒为 NULL

- **位置**：`CreateBookingRequest.java:46` → `BookingServiceImpl.java:178-189`（`buildBooking` 从不 `setAttendees`）；`IdempotencyServiceImpl.java:62-68`（从不写 `bookingId`）；`schema.sql:117,147`。
- **复现**：提交带 `attendeeIds` 的请求，创建成功后查库 `bookings.attendees` 为 NULL；任一成功预订对应的 `idempotency_keys.booking_id` 也为 NULL。
- **实际 vs 期望**：实际「静默丢弃用户输入」；期望「参会人落库并可回显，幂等记录可追溯到 booking」。
- **影响**：`PR-06` 参会人管理、`PR-10` 审计追溯缺少数据基础；用户以为填了参会人实际没生效。
- **改进方案**：补全 DTO→Entity 映射与幂等结果回写；参会人 ID 做有效性校验；补断言字段的集成测试。
- **转化**：`OPT-05`（产品形态见 `PR-06`）

### BUG-10 [P2] 配置硬编码、登出空实现、异常映射缺口

- **位置**：`application.yml:5,7,22-24`；`application-prod.yml:13-14`；`AuthController.java:53-56`；`GlobalExceptionHandler.java:73-90,99-105`。
- **复现**：prod 启动时未显式覆盖 profile → 仍加载 `dev` 的 localhost 库；`spring.jackson.date-format` 对 `LocalDateTime` 不生效，期望 `yyyy-MM-dd HH:mm:ss` 实得 ISO-8601；未注入 `DB_PASSWORD` 直接启动失败；`@Validated` 类级别 `@NotBlank` 触发的 `ConstraintViolationException` / `HandlerMethodValidationException` 未显式映射，可能落入 500（**待验证**：Spring 6.1 解析优先级）；调用 `/auth/logout` 返回成功但服务端 token 仍有效 8 小时。
- **实际 vs 期望**：实际「配置靠约定、登出靠自觉、错误码有盲区」；期望「profile/密钥由启动参数注入，logout 有服务端失效语义，所有校验异常都映射 400」。
- **改进方案**：profile 由环境变量注入；删除无效 `date-format` 改为显式 `JavaTimeModule` 配置；补 `ConstraintViolationException` / `HandlerMethodValidationException` handler；`logout` 落地吊销清单或缩短 TTL + refresh（决策见 §9 维度 4）。
- **转化**：`OPT-01`、`OPT-05`

### BUG-11 [P2] 唯一索引含 `deleted_at` 失效 + 逻辑删除配置待验证

- **位置**：`schema.sql:62`（`uk_delegate_principal(delegate_id, principal_id, deleted_at)`）、`:83`（`uk_name(name, deleted_at)`）、`:103`（`uk_room_slot` 不区分 status）、`:150`（`expires_at` 注释「定时清理」但无任务）；`application.yml:22-24`。
- **复现**：MySQL 唯一索引中 NULL 不参与比较 → 可插入多条「有效」同名会议室 / 重复有效代订授权；`DelegationGrantMapper.java:17-24` 用 `selectCount>0` 掩盖了重复；`expires_at` 从不参与查询，幂等表无限增长。
- **实际 vs 期望**：实际「软删除与唯一性语义冲突」；期望「有效记录唯一，过期记录可清理」。
- **根因**：把 `deleted_at` 塞进唯一键，却没有配套的非空默认值或生成列；逻辑删除配置字符串是否为 MP 合法值需真实环境验证（**待验证**）。
- **改进方案**：改用「有效标记生成列 + 唯一索引」或非空哨兵值；补 `@SpringBootTest` + 真实 MySQL 验证 `IS NULL` 拼接；加幂等键过期清理定时任务。
- **转化**：`OPT-02`、`OPT-05`

### BUG-12 [P2] 前端表单校验、错误文案与成功后状态收敛缺失

- **位置**：`BookingPage.tsx:41-66,82-88,109-153,160-173`、`api.ts:87-96`、`RoomList.tsx:23-52`、`Login.tsx:10-11,68-76`。
- **复现**：结束时间 ≤ 开始、时长 <15min、非 30min 对齐、人数 > 容量、主题全空格、`Number('')=0` / 小数人数、`principalId` 非整数均不拦；时间字符串硬拼 `${startTime}:00`（`:56-57`），若 `datetime-local` 值自带秒会拼成非法 `…:00:00:00` → 400；失败时原样显示 `[BOOKING_CONFLICT]` / `Network Error` / `timeout of 10000ms exceeded`；成功后结果常驻、表单不重置、房间列表不刷新且无重试入口；登录页预填 `alice/password123` 并公示演示账号。
- **实际 vs 期望**：实际「错误靠服务端试错，成功后状态不一致」；期望「前端即时校验 + 中文错误文案 + 409 引导改时段 + 成功后重置并刷新 + 生产环境不预填凭证」。
- **改进方案**：表单校验规则与 `BookingServiceImpl.java:76-84` 一一对应；错误码→文案映射表；时间值正则规范化；成功后清空结果/轮换 requestId/刷新列表；演示凭证仅 dev 注入；补空态、重试、aria 与 401 测试。
- **转化**：`OPT-05`

### Bug → 优化需求映射表

| 优化需求 | 来源 Bug |
|---|---|
| `OPT-01` 身份与会话安全加固 | BUG-01、BUG-08、BUG-10 |
| `OPT-02` 时段账本与状态机一致性 | BUG-02、BUG-04、BUG-11 |
| `OPT-03` 幂等契约端到端修复 | BUG-03、BUG-06、BUG-07 |
| `OPT-04` 权限边界与数据可见性 | BUG-05 |
| `OPT-05` 交付质量与数据完整性闭环 | BUG-09、BUG-10、BUG-11、BUG-12 |

---

## 6. 新增产品需求（10 项，`PR-01` ~ `PR-10`）

> 统一约定：全部需求**不依赖任何第三方系统或硬件**；验收标准（AC）均可测；指标沿用 `prd.md §6` 非功能基线（列表 P95 < 500ms、写操作 P95 < 800ms、峰值 100 QPS 写入、超卖率 = 0）。每项「依据」只引用现有文档/代码，不虚构业务决策；未定规则统一放入 §9 待确认。

### PR-01 我的预订（列表 + 详情 + 状态筛选）｜优先级 P0

**产品目标与价值**
让用户订完之后还能「找得到、看得懂、管得着」。当前创建成功只在屏幕上留一份 JSON 快照（`BookingPage.tsx:160-166`），刷新即失，用户无法回答「我明天在哪开会」「我的 VIP 申请批了吗」。`docs/api.md:82-83` 已定义 #4/#5 端点但从未实现。这是全部后续管理类需求（`PR-02` / `PR-03` / `PR-07`）的入口。

**用户故事**
> 作为员工，我希望在「我的预订」中按状态与日期查看我创建的、以及代订到我名下的预订，并能打开详情核对会议室、时段、参会人与审批状态，以便安排行程和跟进。

**适用角色**：全部登录用户；`ASSISTANT` 额外看到自己代订出去的记录；`ADMIN` 可按用户维度查看全部（是否默认开放见 §9 维度 4）；`AUDITOR` 只读。

**现状缺口（证据）**
- `docs/api.md:82-83` 定义 `GET /api/bookings`、`GET /api/bookings/{id}`，代码无对应 Controller 方法；
- 前端无路由、无列表页（`frontend/src/` 仅 App / Login / BookingPage / RoomList）；
- `BookingMapper` 只被 `insert` 使用（`BookingServiceImpl.java:92`）；
- `BookingDetailResponse` 不含 `attendees`、`approverId`、`rejectReason`、`cancelReason`（`toResponse` `:210-225`）。

**业务规则**

| 类别 | 规则 |
|---|---|
| 数据范围 | 默认「未来全部 + 过去 30 天」；`booker_id = 我` 或 `principal_id = 我`；软删除记录默认不展示 |
| 筛选 | 状态（PENDING / CONFIRMED / REJECTED / CANCELLED / COMPLETED）、日期区间、会议室、关键词（主题，仅 PUBLIC 或本人记录） |
| 排序/分页 | 按 `start_time` 倒序（未来优先，待确认）；每页 20 条，返回总数 |
| 可见性 | PRIVATE 记录对非相关人只显示「已被预订」，不返回主题与参会人；管理员/审计例外待确认 |
| 详情字段 | 会议室、时段、主题、参会人数/参会人、状态、审批人/驳回原因、取消人/取消原因、代订人/被代订人、创建时间 |
| 异常 | 越权访问他人 PRIVATE 详情 → 404（不暴露资源存在性）；记录不存在 → 404；未登录 → 401 |
| 空态 | 「暂无预订」+ 一键跳转预订页；筛选无结果提示「调整筛选条件」 |

**验收标准**

- `PR-01/AC-1`：创建成功后 3 秒内打开「我的预订」，该记录可见且状态与创建响应一致。
- `PR-01/AC-2`：五类状态筛选结果与 DB 状态逐条一致；组合筛选（状态 + 日期 + 会议室）取交集正确。
- `PR-01/AC-3`：分页 20 条，翻页无重复/遗漏；总数准确。
- `PR-01/AC-4`：`ASSISTANT` 能看到自己代订出去的记录；被代订人能看到代订到自己名下的记录。
- `PR-01/AC-5`：他人 PRIVATE 预订在列表中不出现主题且不返回详情（404）；PUBLIC 预订按 `docs/api.md:217-224` 脱敏展示。
- `PR-01/AC-6`：CANCELLED 记录仅在明确勾选「已取消」时出现，且显示取消原因与取消人。

**指标与非功能**：列表 P95 < 500ms（1000 条数据、20 条/页）；详情 P95 < 500ms；不产生 N+1 查询。

**范围边界（Out of scope）**：不接日历同步；不做跨系统导出；不做消息推送；不引入外部搜索服务。

**依据**：`prd.md:38-45`（US-01）、`docs/api.md:82-83`、`schema.sql:111-138`、`docs/TEST_REVIEW_BASELINE.md:11-13`。

---

### PR-02 取消预订（本人 / 上级 / 管理员）｜优先级 P0

**产品目标与价值**
`prd.md:78-87`（US-05）是核心闭环，但全库无 `DELETE` / 取消代码；用户订错时间只能「等它过去」，同时 `BUG-02` 让槽位永久锁死。取消既是用户可感知能力，也是释放时段账本的唯一业务入口。

**用户故事**
> 作为员工，我希望随时取消自己的预订；作为上级/管理员，我希望在必要时取消他人预订并留下原因，以便会议室重新开放给其他人使用。

**适用角色**：本人（EMPLOYEE / ASSISTANT / MANAGER / APPROVER / ADMIN / 其他）、上级（`superior_id`）、`ADMIN`；`ASSISTANT` 明确**不能**强制取消他人预订（`prd.md:86`、`docs/UNIT_TEST_REPORT.md §2.4`）。

**现状缺口（证据）**
- `docs/api.md:84` 定义 `DELETE /api/bookings/{id}`，无实现；全库无删除 `time_slots` 的代码（`TimeSlotMapper.java:26`）；
- `schema.sql:126-128` 已预留 `cancelled_by` / `cancelled_at` / `cancel_reason` 字段但 Java 从不写入；
- `seed.sql:9-17` 所有用户 `superior_id` 为 NULL，「上级取消下级」缺数据基础（需 `PR-10` 后台或管理员维护该关系，属本系统自身数据）。

**业务规则**

| 类别 | 规则 |
|---|---|
| 可取消状态 | `PENDING` / `CONFIRMED` 可取消；`CANCELLED` / `REJECTED` / `COMPLETED` 不可再取消（重复取消返回同一结果或 409，待确认） |
| 时间边界 | 本人取消：开始前、开始后均可（`prd.md:82`）；已开始会议取消是否释放剩余槽位待确认 |
| 权限 | 本人（booker 或 principal）无需原因；上级取消下级必填原因 ≥5 字（`prd.md:83`）；`ADMIN` 等同上级；`ASSISTANT` 不得强制取消他人 |
| 事务 | 同一事务内：置 `status=CANCELLED`、写 `deleted_at` / `cancelled_by` / `cancelled_at` / `cancel_reason`、删除该 booking 的全部 `time_slots`、写 `audit_logs` |
| 一致性 | 取消提交成功后，该时段立刻可被其他用户预订；并发取消以状态条件更新 + 唯一约束兜底，只允许一个成功 |
| 幂等 | 取消接口同样要求 `X-Request-Id`，重复请求不产生第二条审计 |
| 反馈 | 站内成功提示 + 状态更新；不做邮件/企微/短信通知 |

**验收标准**

- `PR-02/AC-1`：本人取消 CONFIRMED 预订成功，状态变 CANCELLED，`deleted_at` 非空。
- `PR-02/AC-2`：取消后同一会议室同一时段可被另一用户预订成功（验证槽位释放，同时回归 `BUG-02`）。
- `PR-02/AC-3`：上级取消下级时，原因 4 字被拒（400）、5 字通过；`ASSISTANT` 强制取消他人返回 403。
- `PR-02/AC-4`：`PENDING` 取消同样释放槽位并写审计；`COMPLETED` 取消返回 409。
- `PR-02/AC-5`：两个并发取消请求，仅一个成功，审计日志恰好 1 条。
- `PR-02/AC-6`：「我的预订」列表按 `PR-01/AC-6` 正确展示取消原因与取消人。

**指标与非功能**：取消 P95 < 800ms；释放与重订一致性 100%；峰值 100 QPS 下超卖率 = 0。

**范围边界（Out of scope）**：三通道通知、一键取消审批回调、外部工单；「已开始取消」的时段释放口径留待 §9 确认。

**依据**：`prd.md:78-87`、`prd.md:147-166`（取消流程图）、`requirement_review.md §6.4`、`schema.sql:94-106,126-128,158-171`、`docs/TEST_REVIEW_BASELINE.md:126-127`。

---

### PR-03 站内审批工作台（VIP / 大型会议室）｜优先级 P0

**产品目标与价值**
`BookingServiceImpl.java:87-92` 已把 `capacity > 50` 或 VIP 的预订置为 `PENDING`，但 `docs/api.md:85` 的审批端点、`BookingStatus.PENDING/REJECTED`、`BookingEntity.approverId/approvedAt/rejectReason` 全无流转代码——**待审批订单是死单**，且槽位已被占用（`BUG-02`）。本需求把审批做成站内待办，不接任何第三方通道。

**用户故事**
> 作为审批人，我希望在站内「待我审批」列表中查看 PENDING 预订的完整信息，并通过或驳回（驳回必须填原因），以便申请人及时得到结论、会议室资源不被悬置。

**适用角色**：`APPROVER` / `ADMIN`；审批人的确定规则（`approver_id` 如何赋值）当前文档未定义，见 §9 维度 4。

**现状缺口（证据）**
- `docs/api.md:85` 定义 `POST /api/bookings/{id}/approve`，§3.7 无 Response / 错误码（子代理确认设计未完成）；
- 全库无 `approve` / `reject` 方法；`BookingStatus.java` 的 `PENDING` / `REJECTED` 仅枚举；
- `schema.sql:123-125` 预留 `approver_id` / `approved_at` / `reject_reason` 但无写入方；
- `prd.md:73-76` 的验收标准（通过/驳回/超时）零实现。

**业务规则**

| 类别 | 规则 |
|---|---|
| 待办范围 | 仅展示 `status=PENDING` 且当前用户为审批人的记录；默认按 `created_at` 升序；含会议室、时段、人数、主题、预订人/被代订人 |
| 通过 | `PENDING → CONFIRMED`，写 `approver_id` / `approved_at`，槽位保持占用，写审计 |
| 驳回 | `PENDING → REJECTED`，`reject_reason` 必填（≥1 字，长度上限 500），事务内释放该 booking 的全部槽位，写审计 |
| 权限 | 非审批人操作 → 403；对非 PENDING 记录操作 → 409；重复审批 → 409（不允许覆盖结论） |
| 超时 | v1.0 不自动驳回（沿用 `prd.md:76` v1.1 可选），但待办列表标记「已等待 >24h」，由 `OPT-02` 的定时任务负责最终的超时释放策略 |
| 隐私 | PRIVATE 主题对非审批人不脱敏（审批需要信息），但审批完成后按 `PR-01` 规则收敛 |
| 留痕 | 每次通过/驳回写 `audit_logs`，含前后状态与原因 |

**验收标准**

- `PR-03/AC-1`：VIP 会议室创建后状态为 PENDING，出现在对应审批人的待办列表。
- `PR-03/AC-2`：通过后状态 CONFIRMED、`approved_at` 非空，槽位仍占用（他人同段提交返回 409）。
- `PR-03/AC-3`：驳回未填原因返回 400；填原因驳回后状态 REJECTED，槽位释放，他人可重订成功。
- `PR-03/AC-4`：非审批人通过返回 403；对 CONFIRMED 记录审批返回 409；重复审批返回 409。
- `PR-03/AC-5`：通过/驳回各写 1 条审计，含操作人、前后状态、原因。
- `PR-03/AC-6`：待办列表 P95 < 500ms；等待超过 24h 的记录有显式标记。

**指标与非功能**：待办 P95 < 500ms；审批写操作 P95 < 800ms；结论不可篡改（状态条件更新）。

**范围边界（Out of scope）**：企微/邮件一键审批回调、多级审批、会签、审批超时自动驳回（v1.1）、外部审批系统。

**依据**：`prd.md:69-76`、`prd.md:116`、`docs/api.md:85,§3.7`、`schema.sql:122-125`、`docs/TEST_REVIEW_BASELINE.md:11-13,129`。

---

### PR-04 可用时段可视化时间轴与可用性查询｜优先级 P0

**产品目标与价值**
`prd.md:38-44`（US-01）要求按日期+时段展示占用、灰显不可用时段的「当前选定时段是否可用」，但 `RoomServiceImpl.java:32-41` 只返回全量 AVAILABLE 房间，`docs/api.md:80` 的 availability 端点未实现。用户只能盲选时段、提交后靠 409 试错，直接违背「预订平均耗时 <30 秒」与「冲突报错率 <0.1%」两个成功指标。

**用户故事**
> 作为员工，我希望在选会议室之前先看到某天各会议室的时间轴，已占用时段灰显、悬浮显示占用状态，以便一次选对可用时段。

**适用角色**：全部登录用户。

**现状缺口（证据）**
- `docs/api.md:80` 定义 `GET /api/rooms/{id}/availability`，未实现；
- 前端 `RoomList.tsx:23-41` 只在挂载时拉一次房间，与 `BookingPage.tsx:28-29` 的时段状态完全脱钩；
- `schema.sql:94-106` 的 `time_slots` 是唯一占用事实源，但无查询方法；
- `prd.md:42-44` 的「默认展示当天 09:00–18:00」「不可用灰显」「不显示预订人」零实现。

**业务规则**

| 类别 | 规则 |
|---|---|
| 时间轴 | 默认当天 09:00–18:00；30 分钟粒度；可切换日期（最多到 `now + 7天`，与 `BookingServiceImpl.java:47` 一致） |
| 占用来源 | `time_slots` 关联有效预订（`PENDING` / `CONFIRMED`）；`CANCELLED` / `REJECTED` / `COMPLETED` 不占用（由 `OPT-02` 保证数据一致） |
| 展示 | 不可用槽位灰显且不可选；悬浮提示「已被预订」；PRIVATE 不显示主题/预订人；PUBLIC 按 `docs/api.md:217-224` 展示 |
| 一致性 | 时间轴是**预检**不是锁：提交时仍以 `uk_room_slot` 唯一约束为准；若提交返 409，提示「时段刚被占用」并自动刷新时间轴 |
| 边界 | 查询区间 ≤7 天；单次返回 ≤ 房间数 × 18 槽；过去时段一律灰显；维修/退役房间整列不可选 |
| 交互 | 选中空闲连续槽位可自动回填预订表单的起止时间 |

**验收标准**

- `PR-04/AC-1`：某会议室已有 10:00–11:00 预订，时间轴上 10:00、10:30 两格灰显且不可选。
- `PR-04/AC-2`：悬浮灰显格显示「已被预订」，PRIVATE 预订不出现主题与预订人姓名。
- `PR-04/AC-3`：切换日期后重新查询，占用结果与 DB 一致；超过 7 天的日期不可选。
- `PR-04/AC-4`：选中连续空闲槽位后回填表单，起止时间格式与 `CreateBookingRequest.java:34-40` 兼容。
- `PR-04/AC-5`：并发场景下 A 刚订走时段、B 提交 → 返回 409 且 B 的时间轴自动刷新为新状态。
- `PR-04/AC-6`：`MAINTENANCE` / `DECOMMISSIONED` 房间整列不可选（与 `PR-09` 联动）。

**指标与非功能**：availability 查询 P95 < 500ms；首屏时间轴渲染 < 1s（20 间房 × 18 槽）；查询不得放大为逐房间 N+1。

**范围边界（Out of scope）**：日历同步、门禁/IoT 实时占用、跨系统资源预留、拖拽式批量预订（拖拽交互本身可做，但作为本需求的 UI 实现细节，不新增外部依赖）。

**依据**：`prd.md:38-45,114-115,174`、`docs/api.md:80`、`schema.sql:94-106`、`requirement_review.md §6.2`。

---

### PR-05 会议室筛选与搜索｜优先级 P1

**产品目标与价值**
US-01 明确要求按「日期 + 时间段 + 容量 + 设备 + 区域」筛选；当前 `GET /api/rooms` 固定 `status=AVAILABLE` 后按楼层/区域排序返回全量（`RoomServiceImpl.java:32-41`），`RoomListItem` 连 `departmentId` 都不返回（`:43-54`）。房间数量一多，用户只能靠滚动找房间。

**用户故事**
> 作为员工，我希望按参会人数、设备、楼层/区域、VIP 与部门专属条件筛选会议室，以便快速找到合适且我有权限使用的房间。

**适用角色**：全部登录用户；部门专属房间按 `OPT-04` 的权限结果过滤。

**现状缺口（证据）**：`RoomController.java:32-35` 无任何查询参数；`frontend/src/RoomList.tsx` 无筛选 UI；`prd.md:39,42` 的筛选与时间轴要求未落地；`docs/api.md:181` 提到部门专属但无实现。

**业务规则**

| 类别 | 规则 |
|---|---|
| 筛选条件 | 容量 ≥ 输入人数、设备（多选 AND 匹配）、楼层/区域、是否 VIP、状态（默认 AVAILABLE）、部门专属可见性 |
| 组合语义 | 多条件取交集；关键词按会议室名模糊匹配（大小写不敏感，需转义防注入，SQL 一律 `#{}`） |
| 排序 | 默认按「容量最接近参会人数」优先，其次楼层/区域/名称（待确认）；支持按容量、名称排序 |
| 分页 | 每页 20 条，返回总数；无分页参数时保持 v1.0 兼容返回全量（过渡期，待确认） |
| 权限 | 无权限的部门专属房间不得出现在结果中（不仅是灰显）；PRIVATE/敏感字段不返回 |
| 空态 | 「没有符合条件的会议室」+ 一键清空筛选 + 提示放宽人数/设备条件 |
| 与时段联动 | 选定日期/时段后，列表标注该时段是否可用（数据来自 `PR-04`），冲突项排序靠后或置灰 |

**验收标准**

- `PR-05/AC-1`：输入人数 30，结果中所有房间 `capacity ≥ 30`。
- `PR-05/AC-2`：选择「投影仪 + 视频会议」，结果同时具备两项设备；单选「白板」时结果至少含白板。
- `PR-05/AC-3`：楼层/区域 + VIP 组合筛选取交集正确；关键词特殊字符（`'`、`%`）不报错且无 SQL 注入。
- `PR-05/AC-4`：其他部门员工看不到部门专属房间；本部门员工可见且可预订。
- `PR-05/AC-5`：无结果显示空态与「清空筛选」入口；清空后恢复全量可见房间。
- `PR-05/AC-6`：分页 20 条，翻页无重复；与 `PR-04` 联动时可用/不可用标识与 availability 结果一致。

**指标与非功能**：筛选查询 P95 < 500ms（100 间房 / 1000 条预订数据）；不得因设备 JSON 解析失败导致 500（沿用 `RoomServiceImpl.java:60-69` 降级）。

**范围边界（Out of scope）**：照片/全景、实时设备状态（依赖 IoT）、地图导航、外部场地库。

**依据**：`prd.md:38-45,103,120`、`docs/api.md:79,181`、`schema.sql:69-87`、`docs/TEST_REVIEW_BASELINE.md:128`。

---

### PR-06 参会人管理与预订信息完整性｜优先级 P1

**产品目标与价值**
`prd.md:47,50`（US-02）要求「填写会议主题与参会人」，`CreateBookingRequest.java:46` 也接收 `attendeeIds`，但 `BookingServiceImpl.java:178-189` 从不落库，`schema.sql:117` 的 `attendees` 恒为 NULL（`BUG-09`）。用户以为邀请了同事，系统却没有任何记录，后续「我的预订」详情、可见性脱敏、审计都缺数据基础。

**用户故事**
> 作为预订人，我希望从本系统用户中选择参会人并在详情中查看名单，以便会议信息完整、且私密会议的名单不被无关人看到。

**适用角色**：全部登录用户；`PRIVATE` 时仅相关人/管理员可见（待确认）。

**现状缺口（证据）**：`CreateBookingRequest.java:46`、`BookingServiceImpl.java:178-189`、`schema.sql:117`、`BookingDetailResponse`（`toResponse` `:210-225` 无 attendees）、`frontend/src/BookingPage.tsx` 无参会人输入。

**业务规则**

| 类别 | 规则 |
|---|---|
| 数据来源 | 仅从本系统 `users` 表选择在职用户；不接 HR / AD / 通讯录 |
| 录入 | 支持多选、搜索、去重；上限 ≤ `room.capacity`（或 `attendeeCount`，口径待确认）；不允许添加停用/不存在的用户 ID |
| 一致性 | `attendeeCount` 与 `attendeeIds` 的关系需明确（建议 `attendeeCount ≥ attendeeIds.size() + 1`，即含预订人；最终口径见 §9 维度 1），不一致时以明确错误提示拒绝，不静默修正 |
| 落库 | `attendees` JSON 存 user_id 快照；用户后续停用/删除不影响历史展示（展示名取快照或降级为「已离职用户」） |
| 可见性 | PRIVATE 预订的参会人名单对非相关人隐藏；PUBLIC 按 `docs/api.md:217-224` 展示 |
| 回显 | 创建响应、列表、详情（`PR-01`）、审批待办（`PR-03`）均返回参会人 |
| 隐私 | 不返回邮箱/手机号等 PII（`schema.sql:18-19`） |

**验收标准**

- `PR-06/AC-1`：提交含 3 个 `attendeeIds` 的预订，`bookings.attendees` 落库且详情接口回显 3 人。
- `PR-06/AC-2`：重复 ID 自动去重；不存在或已停用的用户 ID 返回 400 并指明具体 ID。
- `PR-06/AC-3`：`attendeeIds` 数量超过所选房间容量时返回明确错误，不静默截断。
- `PR-06/AC-4`：`attendeeCount` 与名单不一致时按确认口径拒绝，错误信息可指导用户修改。
- `PR-06/AC-5`：PRIVATE 预订的参会人名单对非相关人不可见；PUBLIC 正常展示且不含 PII。
- `PR-06/AC-6`：停用某参会人后，历史详情仍可展示（不 500、不丢记录）。

**指标与非功能**：详情 P95 < 500ms；参会人上限与房间容量一致；JSON 字段解析失败降级为空列表且不阻断主流程。

**范围边界（Out of scope）**：外部联系人、组织架构同步、邮件/IM 邀请、参会人确认回执。

**依据**：`prd.md:46-58`、`CreateBookingRequest.java:46`、`schema.sql:117`、`docs/api.md:217-224`。

---

### PR-07 预订改期（修改预订）｜优先级 P1

**产品目标与价值**
填错时间、临时换大会议室是最高频的变更场景。当前连取消都没有实现（`PR-02`），用户只能「放弃原预订 + 重新创建」，而原预订的槽位因 `BUG-02` 永远不释放——等于一次填错永久损失一个时段。改期是把「预订」从一次性动作变成可管理对象的必要能力。

**用户故事**
> 作为预订人，我希望在会议开始前修改预订的时间、会议室、主题、人数或参会人，系统自动重新校验冲突并保留变更记录，以便不用取消重订。

**适用角色**：本人（booker / principal）、`ADMIN`；`ASSISTANT` 仅可改自己代订且在授权有效期内的记录（待确认）。

**现状缺口（证据）**：全库无 `PUT` / `PATCH` / `update` 业务接口；`BookingMapper` 仅被 `insert` 调用；`schema.sql:129-130` 有 `updated_at` 但无业务更新路径；`audit_logs` 零引用。

**业务规则**

| 类别 | 规则 |
|---|---|
| 可改状态 | 仅 `CONFIRMED` / `PENDING`；已开始（可选）、`CANCELLED` / `REJECTED` / `COMPLETED` 不允许 |
| 可改字段 | 起止时间、会议室、主题、参会人数、参会人、可见性；`booker_id` / `principal_id` / `id` 不可改 |
| 重新校验 | 每次改期重跑时间规则、容量、房间状态、部门权限、代订授权、冲突判定与审批分流（容量 >50 或 VIP → 需审批） |
| 事务 | 同一事务内：释放旧槽 → 按新时段写槽 → 更新 booking → 写审计（含前后快照）；任一冲突整体回滚，旧预订保持原状 |
| 状态影响 | 普通 → VIP/大会议室改期时 `CONFIRMED → PENDING` 并进入 `PR-03` 待办；PENDING 改期是否重置审批结论待确认 |
| 并发 | 以 `uk_room_slot` 唯一约束 + 状态条件更新兜底，两个并发改期只有一个成功 |
| 留痕 | 每次改期写 `audit_logs`（`BOOKING_UPDATE` + 前后快照），详情页可查看变更历史（是否对普通用户开放待确认） |

**验收标准**

- `PR-07/AC-1`：把 10:00–11:00 改到 14:00–15:00 成功，旧时段可被他人预订，新时段被占用。
- `PR-07/AC-2`：改到已占用时段返回 409，且原预订的时段、状态、字段完全不变（事务回滚）。
- `PR-07/AC-3`：从普通小会议室改到 VIP 会议室后状态变 PENDING，并出现在审批待办。
- `PR-07/AC-4`：改人数超过新会议室容量返回 400；改期到过去时间/超过 7 天返回 400。
- `PR-07/AC-5`：对 CANCELLED / COMPLETED 或非本人预订改期返回 403/409。
- `PR-07/AC-6`：每次改期产生 1 条含前后快照的审计记录；两个并发改期只有一个成功。

**指标与非功能**：改期 P95 < 800ms；改期后超卖率 = 0；旧槽释放与新槽占用原子。

**范围边界（Out of scope）**：批量改期、循环会议/系列会议、跨系统同步、改期审批的第三方通知。

**依据**：`prd.md:46-58,114-118`、`schema.sql:111-138,158-171`、`requirement_review.md §6.1-6.2`。

---

### PR-08 代订授权自助管理｜优先级 P1

**产品目标与价值**
`prd.md:60-67`（US-03）要求「下拉选择被代订人（仅显示授权关系内的人）」，实际 `BookingPage.tsx:99-105` 让助理**手输数字 ID**；授权关系只能靠 `seed.sql:20-23` 手工 INSERT，`delegation_grants` 的 `valid_from/valid_to`（`schema.sql:57-58`）虽被 `DelegationGrantMapper.existsValid` 使用（`:16-24`），却没有任何创建/撤销/到期管理入口。授权一旦写入就永久有效，是权限治理缺口。

**用户故事**
> 作为被代订人（领导）或管理员，我希望自助创建、查看、撤销代订授权并设置有效期；作为助理，我希望从有效授权的下拉列表中选择被代订人，以便不用记用户 ID、也不会误代订。

**适用角色**：被代订人本人、`ADMIN`、`ASSISTANT`（只读自己的有效授权）。

**现状缺口（证据）**：`seed.sql:20-23`（手工授权）、`BookingPage.tsx:91-107`（手输 ID）、`DelegationGrantMapper.java:16-24`（只查不用）、`schema.sql:53-64`（表存在但唯一键失效，见 `BUG-11`）、`prd.md:64`。

**业务规则**

| 类别 | 规则 |
|---|---|
| 授权创建 | 由被代订人本人或 ADMIN 创建；`delegate_id` 必须是 `ASSISTANT`（或 ADMIN 特批，待确认）；不能给自己授权；不能创建重复有效授权 |
| 有效期 | `valid_from` / `valid_to` 可空（空 = 长期有效）；`valid_to < today` 自动失效；到期不可续期只能新建（待确认） |
| 撤销 | 被代订人 / ADMIN 可撤销；撤销走软删除，写审计；撤销后已有 PENDING 预订的处理待确认 |
| 助理侧 | 代订表单只列出「当前有效授权」的被代订人，展示姓名而非数字 ID；手输 ID 通道关闭 |
| 校验 | 提交预订时仍以 `existsValid` 服务端校验为准，前端下拉不构成授权 |
| 唯一性 | 由 `OPT-05` 修复 `uk_delegate_principal(..., deleted_at)` 的 NULL 语义，保证同一组合只有一条有效授权 |
| 留痕 | 创建/撤销写 `audit_logs`，含双方与有效期 |

**验收标准**

- `PR-08/AC-1`：被代订人创建授权（含起止日期）后，助理下拉立即出现该人，且无需手输 ID。
- `PR-08/AC-2`：授权 `valid_to` 为昨天时，助理下拉不出现该人；直接构造请求提交返回 403。
- `PR-08/AC-3`：撤销授权后，助理既看不到也不能再代订；历史已完成的预订不受影响。
- `PR-08/AC-4`：同一 `(delegate, principal)` 不能创建两条有效授权；重复提交返回 409。
- `PR-08/AC-5`：助理给自己授权、非被代订人给他人授权均返回 403。
- `PR-08/AC-6`：创建与撤销各写 1 条审计记录。

**指标与非功能**：授权列表 P95 < 500ms；授权变更在 3 秒内对助理可见；所有授权判定以服务端为准。

**范围边界（Out of scope）**：HR 组织架构同步、多级代理链、外部审批流、代订申请的消息推送。

**依据**：`prd.md:60-67`、`schema.sql:53-64`、`DelegationGrantMapper.java:16-24`、`docs/UNIT_TEST_REPORT.md §2.4`。

---

### PR-09 会议室管理后台与维护时段｜优先级 P1

**产品目标与价值**
`prd.md:99-106`（US-07）要求会议室 CRUD 与「置为 MAINTENANCE/DECOMMISSIONED 时级联自动取消未开始预订」；当前 `schema.sql:78` 有三个状态但没有任何流转接口，管理员无法新增/停用房间，维修时既无法预告影响也无法处理已订记录。

**用户故事**
> 作为管理员，我希望维护会议室资料与状态，并在把房间置为维修/退役前看到受影响的预订清单、二次确认后统一处理，以便运营动作可控、可追溯。

**适用角色**：仅 `ADMIN`（`AUDITOR` 只读查看）。

**现状缺口（证据）**：`docs/api.md` 无会议室写接口；`RoomServiceImpl.java` 只有 `listAvailable`；`RoomController.java:30` 注释预留部门过滤但未使用；`schema.sql:78,87` 状态枚举无流转代码；`prd.md:104` 的级联取消零实现。

**业务规则**

| 类别 | 规则 |
|---|---|
| CRUD 字段 | `name`、`capacity`、`equipment[]`、`floor`、`area`、`is_vip`、`department_id`、`status`（`prd.md:103`）；名称唯一性由 `OPT-05` 修复 |
| 权限 | 所有写操作仅 ADMIN；非 ADMIN 返回 403；每次变更写审计含前后快照 |
| 状态机 | `AVAILABLE ⇄ MAINTENANCE → DECOMMISSIONED`；`DECOMMISSIONED` 是否可逆待确认（§9 维度 3） |
| 影响预览 | 状态变更前先返回受影响预订清单：数量、时段、预订人、状态；二次确认才执行 |
| 级联取消 | 事务内：取消未开始（`start_time > now`）的 PENDING/CONFIRMED 预订、释放槽位、写取消原因、写审计；已开始的预订不自动取消（待确认） |
| 容量/VIP 变更 | 修改 `capacity` / `is_vip` 会影响审批分流（`BookingServiceImpl.java:87`），需提示「仅对新预订生效」，不回溯已有订单 |
| 可见性 | 维修/退役房间从 `PR-05` 列表与 `PR-04` 时间轴隐藏或整列置灰 |

**验收标准**

- `PR-09/AC-1`：ADMIN 新增/编辑/停用会议室成功，列表与详情一致；非 ADMIN 全部写操作 403。
- `PR-09/AC-2`：提交同名会议室返回 409（验证 `BUG-11` 修复）。
- `PR-09/AC-3`：把有 3 条未来预订的房间置为 MAINTENANCE，预览清单显示 3 条，取消执行后 3 条均 CANCELLED 且槽位可重订。
- `PR-09/AC-4`：已开始的预订不被级联取消；取消预览中不出现。
- `PR-09/AC-5`：每次状态变更写审计（前后状态 + 受影响预订数）；取消执行幂等，重复点击不产生第二条审计。
- `PR-09/AC-6`：维修房间在 `PR-05` 列表隐藏、在 `PR-04` 时间轴整列不可选。

**指标与非功能**：影响预览 P95 < 500ms；级联取消（≤100 条预订）在单事务内完成且 P95 < 2s；失败整体回滚。

**范围边界（Out of scope）**：设备 IoT 联动、外部工单/资产系统、照片上传、多渠道通知（站内提示即可）。

**依据**：`prd.md:99-106`、`schema.sql:69-87`、`prd.md:147-166`（取消流程）、`docs/TEST_REVIEW_BASELINE.md:130`。

---

### PR-10 运营看板与审计日志｜优先级 P2

**产品目标与价值**
`prd.md:13-20` 把「会议室平均利用率 ≥60%」定为首要成功指标，`prd.md:105-106` 要求审计日志筛选导出与看板（利用率、趋势、长占用、高频占用、强制取消次数），`schema.sql:158-171` 也建了 `audit_logs` 表——但 Java 侧零引用，指标无法度量、操作无法追溯，`prd.md:177` 的「审计日志保留 ≥1 年」也无法落地。

**用户故事**
> 作为管理员/审计员，我希望在站内看板查看利用率与异常占用，并按操作人、对象、动作、时间范围检索审计记录，以便评估运营效果与追溯争议操作。

**适用角色**：`ADMIN`（全部）、`AUDITOR`（只读）、`MANAGER`（仅本部门数据，待确认）。

**现状缺口（证据）**：`schema.sql:158-171,173-192` 两表零 Java 引用；`R.traceId` 恒 null；`docs/api.md` 无看板/审计端点；`docs/TEST_REVIEW_BASELINE.md:11-13` 将 US-07 列为几乎零覆盖。

**业务规则**

| 类别 | 规则 |
|---|---|
| 指标口径 | 利用率 = 统计周期内「已占用槽位数」/「可预订槽位数」；可预订槽位按 09:00–18:00 × 30min × 可用房间数计算（口径待确认）；按日/周/会议室/部门分组 |
| 趋势 | 预订量、取消量、审批通过/驳回量、强制取消次数的时间序列；支持日期区间 |
| 异常占用 | 单次 >8h 标记 🟡、7 日累计 >20h 标记 🔴（沿用 `requirement_review.md §7.5` 软约束，只标记不阻断、不惩罚） |
| 审计字段 | 操作人、动作（BOOKING_CREATE / BOOKING_CANCEL / BOOKING_UPDATE / BOOKING_APPROVE / ROOM_UPDATE / GRANT_CREATE…）、对象类型/ID、前后快照、原因、时间、IP |
| 筛选导出 | 按 operator / target / action / 时间范围筛选；站内 CSV 导出（本系统自身能力，不外推） |
| 写入点 | 由 `PR-02` / `PR-03` / `PR-07` / `PR-08` / `PR-09` 在各自事务内写审计，保证「业务成功 = 留痕成功」 |
| 权限与隐私 | AUDITOR 只读且不可导出他人 PII；PRIVATE 主题在看板/审计中脱敏；审计记录不可编辑删除 |
| 保留 | ≥1 年（`prd.md:177`）；超期归档策略待确认 |

**验收标准**

- `PR-10/AC-1`：给定 2 间房、1 天、共 36 个可预订槽，占用 18 个时看板利用率显示 50%，与手工计算一致。
- `PR-10/AC-2`：趋势图按日/周切换，预订量/取消量与 DB 统计一致；强制取消次数只统计上级/管理员/维修级联触发的取消。
- `PR-10/AC-3`：单次 9h 预订被标 🟡、7 日累计 21h 被标 🔴，且**不阻断**任何预订操作。
- `PR-10/AC-4`：审计按四类条件组合筛选正确；CSV 导出字段与页面一致、含 UTF-8 BOM 可直接打开。
- `PR-10/AC-5`：PRIVATE 主题在看板与审计中脱敏；非 ADMIN/AUDITOR 访问看板返回 403。
- `PR-10/AC-6`：`PR-02` 取消、`PR-03` 审批、`PR-07` 改期、`PR-09` 状态变更各产生恰好 1 条审计，业务回滚时审计同步回滚。

**指标与非功能**：看板 P95 < 1s（1 年数据范围内）；审计写入不阻塞主流程但必须与业务同事务；审计保留 ≥1 年。

**范围边界（Out of scope）**：BI/数仓外推、跨系统报表、实时大屏推送、第三方监控告警。

**依据**：`prd.md:13-20,99-106,175-177`、`requirement_review.md §7.5`、`schema.sql:158-171`、`docs/TEST_REVIEW_BASELINE.md:11-13`。

---

## 7. Bug 转化优化需求（5 项，`OPT-01` ~ `OPT-05`）

> 与 `PR-*` 的区别：`OPT-*` 修复**既有实现的错误行为**，改动集中在安全、数据一致性、契约与交付质量；`PR-*` 新增**用户可感知的产品能力**。两者在同一模块可能同时落地（例如 `OPT-02` 提供槽位释放机制，`PR-02` 提供取消入口）。

### OPT-01 身份与会话安全加固｜优先级 P0｜来源 BUG-01、BUG-08、BUG-10

**目标**：消除「可伪造任意身份」与「客户端缓存即凭据」两类风险，让「退出登录」具备真实语义。

**改进方案**

1. `TokenService.java:32` 去掉默认值，改为 `${APP_AUTH_SECRET}`；`init()` 校验长度 ≥32 且不等于历史默认值，失败抛异常阻止启动（fail-fast），dev 通过显式 env 提供。
2. `application-prod.yml` / `application-dev.yml` 不落任何密钥明文；补充「密钥缺失时启动失败」的集成测试。
3. 前端 `App.tsx` 增加 `bootstrapping` 态：有 token 才调已存在的 `/auth/me`（`api.ts:106-108`），以服务端返回的 `role` 覆盖本地缓存；失败 `clearSession()`；校验期间只显示加载态。
4. 401 事件区分「登录接口 401（密码错误，不清 session）」与「业务接口 401（会话过期，提示后切登录页）」；避免并发 401 重复派发。
5. `AuthController.java:53-56` 的 logout 落地为服务端可失效语义（吊销清单 / 短 TTL + refresh，方案见 §9 维度 4 决策后再定），至少先缩短 TTL 并补「登出后旧 token 不可用」测试。
6. 补 `App` / `Login` / `auth` 的登录态恢复、伪造缓存、token 失效三类前端测试。

**验收标准**

- `OPT-01/AC-1`：未设置 `APP_AUTH_SECRET` 时应用启动失败并给出明确错误；设置弱密钥（<32）同样失败。
- `OPT-01/AC-2`：用源码中旧默认密钥签发的 token 一律返回 401。
- `OPT-01/AC-3`：篡改 `mr_user.role=ADMIN` 后刷新，UI 角色与 `/auth/me` 一致且代订入口按真实角色显示。
- `OPT-01/AC-4`：删除 token 保留 user 缓存后刷新，自动回到登录页，不出现「假登录」。
- `OPT-01/AC-5`：按选定方案实现后，登出/吊销的旧 token 不能再调用任何需鉴权接口。
- `OPT-01/AC-6`：登录密码错误（401）不会清空已有会话，也不跳「会话已过期」提示。

**风险/注意**：改密钥会使所有已签发 token 立即失效，需安排发布窗口；env-only 与 `application-prod.yml` 现有 `${DB_PASSWORD}` 的处理方式保持一致。

---

### OPT-02 时段账本与状态机一致性｜优先级 P0｜来源 BUG-02、BUG-04、BUG-11

**目标**：把「只增不减的占用表」修成有借有贷、可收敛的账本，消除会议室时段永久锁死。

**改进方案**

1. 先做产品决策：15min 最短时长与 30min 槽位如何统一、末槽是否允许越界、最长时长上限（§9 维度 1）。决策后在 `BookingServiceImpl.validateTimeRange` 补 end 对齐、时长上限、`end ≤ now+7d`；`buildSlots` 按新规则生成，禁止 `slot_end` 超过 `booking.end_time`。
2. 定义状态机权威口径并写入文档：`PENDING` 是否占槽、`REJECTED` / `CANCELLED` / `COMPLETED` 何时释放、`CANCELLED → COMPLETED` 是否合法（当前 `schema.md §3.1` 允许，应禁止）。
3. `TimeSlotMapper` 增加 `deleteByBookingId`；所有释放路径（取消、驳回、改期、维护级联、PENDING 超时）在同一事务内调用，禁止只改 `status` 不删槽。
4. 启用定时任务（`@EnableScheduling`）：PENDING 超时处理、CONFIRMED → COMPLETED 推进、幂等键过期清理（`schema.sql:150`）；任务需幂等、可重入、有执行日志。
5. `uk_room_slot(room_id, slot_start)` 与状态语义对齐（可选：把占用表拆为「有效占用视图」或加 `effective` 生成列），并补真实 MySQL 并发测试验证零超卖。
6. 补 `@SpringBootTest` + Testcontainers 验证 `@TableLogic` 的 `"null"` / `"now()"` 配置（`application.yml:22-24`）确实生成 `IS NULL`；若不合法则改为 MP 标准配置。

**验收标准**

- `OPT-02/AC-1`：取消/驳回/超时后，该 booking 的全部槽位被删除，同段可被新预订成功。
- `OPT-02/AC-2`：`09:00–09:45` 类非对齐结束时间按决策结果被拒或按明确规则占用，且末槽不越界；`end` 超 7 天被拒。
- `OPT-02/AC-3`：定时任务连续运行 2 次不重复释放、不误删有效预订。
- `OPT-02/AC-4`：`CANCELLED` 记录不能再流转为 `COMPLETED`；状态机图与代码一致。
- `OPT-02/AC-5`：真实 DB 并发（两用户同段提交）恰好一个成功，超卖率 = 0。
- `OPT-02/AC-6`：逻辑删除配置在真实 MySQL 下 `selectList` / `selectById` 均返回有效记录（验证 `BUG-11` 待验证项）。

**风险/注意**：状态机口径变更会影响历史数据，需一次性数据修复脚本（把历史 PENDING/已结束预订占用的槽位按新规则释放），并在上线前备份。

---

### OPT-03 幂等契约端到端修复｜优先级 P0｜来源 BUG-03、BUG-06、BUG-07

**目标**：让「重复提交返回首次结果」真正成立，且不泄露他人数据、不产生半成品状态。

**改进方案**

1. DB/查询口径统一到三元组 `(request_id, user_id, endpoint)`：改联合主键或在 `loadResponse` 显式比对三者与 `expires_at`；不一致返回 409（不返回他人响应）。
2. `X-Request-Id` 服务端限长 ≤64 + 格式白名单（UUID），超长返回 400 而非 500（`schema.sql:144`）。
3. 拆分 `IdempotencyServiceImpl.executeOnce` 的 try/catch：只有「占位 INSERT」捕获 `DuplicateKeyException`，`action.get()` 移出 catch，避免业务侧 DuplicateKey 被误判为重放。
4. `persistResponse` 失败必须抛异常触发业务回滚；补 PROCESSING / DONE 状态，处理中的重放返回 409 或等待后重试，禁止返回 `response=NULL` 的记录。
5. 前端把 `requestId` 上移到表单会话：挂载时生成、失败重试复用、成功后轮换、关键字段变更时轮换（`api.ts:121-127` → `BookingPage.tsx`）。
6. 补测试：跨用户同 ID、跨 endpoint 同 ID、过期 ID、序列化失败、并发同 ID、超时重试复用。

**验收标准**

- `OPT-03/AC-1`：用户 B 复用 A 的 requestId 返回 409/400，响应体不含 A 的任何预订字段。
- `OPT-03/AC-2`：同一用户同一 endpoint 重放返回与首次完全一致的响应，且 DB 只有 1 条 booking。
- `OPT-03/AC-3`：响应快照写失败时 booking 回滚，库中不留「已创建但无幂等记录」的脏数据。
- `OPT-03/AC-4`：超过 64 字符的 `X-Request-Id` 返回 400；`ConstraintViolationException` 类异常不落到 500。
- `OPT-03/AC-5`：前端超时后点击重试复用同一 requestId，不产生误导性 409；成功后 ID 轮换。
- `OPT-03/AC-6`：并发同 ID 的两个请求至多创建一个 booking，返回内容一致。

**风险/注意**：主键变更需数据迁移；`PR-02` / `PR-07` 等新写接口应复用同一幂等组件，避免各写各的。

---

### OPT-04 权限边界与数据可见性｜优先级 P1｜来源 BUG-05

**目标**：把文档里的「部门专属」和「PRIVATE 脱敏」变成服务端强制约束，并堵住脏 `principalId`。

**改进方案**

1. `CurrentUser`（`security/CurrentUser.java:16`）补 `departmentId`（由 `AuthInterceptor.java:38-43` 回查用户时一并填入）；`UserInfoResponse` 视需要暴露部门信息。
2. `RoomServiceImpl.listAvailable` 支持按 `department_id IS NULL OR department_id = 当前部门` 过滤；`RoomListItem` / 前端 `Room` 类型补部门标签；`RoomController.java:30` 的预留入参真正使用。
3. `BookingServiceImpl.loadAndValidateRoom` 在创建路径二次校验部门归属（防止绕过列表直接 POST）；`validateDelegation` 增加 `principalId` 必须是有效在职用户的校验。
4. 按 `docs/api.md:217-224` 落地 PRIVATE 脱敏：列表/时间轴/详情按「本人 / 被代订人 / ADMIN / AUDITOR」判定，PRIVATE 的主题与参会人对其他人隐藏。
5. 明确 `RECEPTIONIST` / `AUDITOR` 两个枚举角色的权限矩阵（当前全文档未定义，见 §9 维度 4），至少定义「只读范围」。
6. 补测试：跨部门列表/预订、无权限房间、不存在 principalId、PRIVATE 脱敏、角色变更后旧 token 不提权。

**验收标准**

- `OPT-04/AC-1`：其他部门员工的房间列表与搜索结果中不含部门专属房间。
- `OPT-04/AC-2`：直接构造请求预订他部门专属房间返回 403。
- `OPT-04/AC-3`：传入不存在/停用用户的 `principalId` 返回 400，不产生脏 booking。
- `OPT-04/AC-4`：PRIVATE 预订对非相关人不返回主题、参会人、预订人；PUBLIC 按契约展示。
- `OPT-04/AC-5`：`RECEPTIONIST` / `AUDITOR` 有明确权限表且被测试覆盖。
- `OPT-04/AC-6`：用户角色/部门变更后，旧 token 下一次请求即使用新权限。

**风险/注意**：部门字段依赖 `users.department_id` 数据质量；`seed.sql` 当前未填部门，需补演示数据或允许「无部门用户」的明确规则。

---

### OPT-05 交付质量与数据完整性闭环｜优先级 P1｜来源 BUG-09、BUG-10、BUG-11、BUG-12

**目标**：修复「接收了但没落库」「配置靠约定」「错误不可读」「唯一键失效」「零集成测试」五类交付缺陷，让前述 14 项需求有可验证的地基。

**改进方案**

1. 数据完整性：补全 `attendeeIds → bookings.attendees` 映射（`OPT` 侧）与 `idempotency_keys.booking_id` 回写；补断言字段的集成测试；产品展示由 `PR-06` 承接。
2. 配置：移除 `application.yml:5` 硬编码 `profiles.active: dev`，改启动参数/env；移除对 `LocalDateTime` 无效的 `date-format`，显式配置 `JavaTimeModule`；`DB_PASSWORD` 保留 env-only 但补启动前检查与清晰报错；生产库账号改最小权限而非 root。
3. 异常映射：`GlobalExceptionHandler` 补 `ConstraintViolationException` / `HandlerMethodValidationException` / `HttpMessageNotReadableException`，统一 400 + `INVALID_PARAM`，避免空请求头/畸形 JSON 落到 500。
4. 唯一键：把 `uk_name(name, deleted_at)` 与 `uk_delegate_principal(..., deleted_at)` 改为「有效标记生成列 + 唯一索引」或非空哨兵值；补真实 MySQL 的重复插入测试。
5. 前端体验：表单校验与 `BookingServiceImpl.java:76-84` 一一对应；时间值正则规范化（避免 `:00:00:00`）；错误码→中文文案映射 + 409 引导改时段 + 网络/超时兜底；成功后重置表单、轮换 requestId、刷新房间列表；列表加载失败提供重试按钮；演示凭证仅 dev 注入。
6. 测试地基：引入 `@SpringBootTest` / MockMvc / Testcontainers，覆盖 AuthInterceptor 401 与白名单、`X-Request-Id` 必填、`@Valid` 校验、`R<T>` 异常映射、事务回滚、mapper XML；前端补 `App` / `Login` / `auth` 与重复提交、成功后状态收敛测试；把 `test/setup.ts` 固定 `randomUUID` 的桩改为可控但可变的序列，避免继续掩盖 `BUG-07`。
7. 工程门禁：补 CI（后端单测 + 前端 `vitest` + `tsc --noEmit`）、覆盖率报告与 lint；`README.md`（当前 0 字节）补启动、环境变量、种子数据顺序、部署说明；修正失效交叉引用与 `seed_rooms.sql:3` 的注释/执行顺序（`seed.sql:26-28` 只插 2 间房，`UPDATE ... WHERE id=3` 匹配 0 行）。

**验收标准**

- `OPT-05/AC-1`：提交含参会人的预订后，`bookings.attendees` 与 `idempotency_keys.booking_id` 均非 NULL。
- `OPT-05/AC-2`：prod 不显式指定 profile 时不会连到 dev 的 localhost 库；缺 `DB_PASSWORD` 有明确启动错误。
- `OPT-05/AC-3`：畸形 JSON / 空 `X-Request-Id` / 类级别校验失败均返回 400 + `INVALID_PARAM`，无 500。
- `OPT-05/AC-4`：同名会议室与重复有效授权的第二次插入返回 409。
- `OPT-05/AC-5`：结束时间早于开始、人数超容量、非 30min 对齐等在前端即时提示；409 显示中文引导；成功后表单重置且列表刷新。
- `OPT-05/AC-6`：CI 中至少 1 个真实 DB 集成测试与 1 个 MockMvc 契约测试通过；`README.md` 能让新人按步骤启动。

**风险/注意**：本项横跨前后端与构建，建议拆成 2~3 个迭代交付，先做 1~4（数据/配置/异常/唯一键），再做 5~7（体验/测试/文档），避免与 `PR-*` 大范围改同一文件互相冲突。

---

## 8. 15 项需求总表与实施路线图

### 8.1 总表

| 编号 | 名称 | 类型 | 优先级 | 依赖 | 主要价值 |
|---|---|---|---|---|---|
| `PR-01` | 我的预订（列表/详情/状态筛选） | 产品 | P0 | `OPT-05` | 预订后可见、可管理 |
| `PR-02` | 取消预订（本人/上级/管理员） | 产品 | P0 | `OPT-02` | 闭环 + 槽位释放入口 |
| `PR-03` | 站内审批工作台 | 产品 | P0 | `OPT-02` | 解 PENDING 死单 |
| `PR-04` | 可用时段可视化时间轴 | 产品 | P0 | `OPT-02`、`OPT-05` | 降低冲突报错率、支撑 <30s |
| `PR-05` | 会议室筛选与搜索 | 产品 | P1 | `OPT-04` | 找得到合适房间 |
| `PR-06` | 参会人管理与信息完整性 | 产品 | P1 | `OPT-05` | 信息不丢失、隐私可控 |
| `PR-07` | 预订改期 | 产品 | P1 | `PR-01`、`OPT-02` | 订错可改，不用取消重订 |
| `PR-08` | 代订授权自助管理 | 产品 | P1 | `OPT-04`、`OPT-05` | 权限可治理、告别手输 ID |
| `PR-09` | 会议室管理后台与维护时段 | 产品 | P1 | `PR-02`、`OPT-04` | 运营可控、级联可追溯 |
| `PR-10` | 运营看板与审计日志 | 产品 | P2 | `PR-02/03/07/08/09` | 度量 ≥60% 利用率、可追溯 |
| `OPT-01` | 身份与会话安全加固 | 优化 | P0 | 无 | 消除可伪造身份（发布阻塞） |
| `OPT-02` | 时段账本与状态机一致性 | 优化 | P0 | 业务决策（§9 维度 1/3） | 消除时段永久锁死（发布阻塞） |
| `OPT-03` | 幂等契约端到端修复 | 优化 | P0 | 无 | 消除越权与重试乱象（发布阻塞） |
| `OPT-04` | 权限边界与数据可见性 | 优化 | P1 | `OPT-01` | 部门专属/脱敏/脏数据 |
| `OPT-05` | 交付质量与数据完整性闭环 | 优化 | P1 | 无 | 地基可验证、可维护 |

> 合计：10 项产品需求 + 5 项优化需求 = **15 项新增需求**；另有 12 条 Bug 作为追溯依据，不重复计数。

### 8.2 实施路线图（建议三批）

| 批次 | 内容 | 完成判据 |
|---|---|---|
| **第 1 批：发布阻塞（P0）** | `OPT-01`、`OPT-02`、`OPT-03`；同步确认 §9 维度 1/3 的业务决策 | 默认密钥不可用、取消/驳回可释放槽位、跨用户幂等不越权；真实 DB 并发测试通过 |
| **第 2 批：最小可用闭环** | `PR-01`、`PR-02`、`PR-03`、`PR-04`，穿插 `OPT-04` 的权限部分 | 用户能「查 → 订 → 取消 → 审批」，且预订前能看到时段占用；冲突报错率可度量（「改期」由 `PR-07` 在第 3 批提供） |
| **第 3 批：完整运营与质量** | `PR-05`~`PR-10`、`OPT-04` 剩余、`OPT-05` | 44 条 AC 的 ❌23 条收敛；看板能量化 ≥60% 利用率；CI 有集成测试门禁 |

### 8.3 未纳入 15 项、但值得进 backlog 的能力（站内、不依赖第三方）

- **站内消息中心**：把 `notifications` 表（`schema.sql:173-192`）改作站内信，承载预订成功/驳回/取消等状态变更提示。本次未纳入，因为 `PR-01`~`PR-03` 的页面内反馈已能覆盖最小闭环，且它容易与「三通道推送」混淆。
- **配额软约束的产品化**：`requirement_review.md §7.5` 的 >4h 确认框、>8h / 7 日 >20h 标记，可在 `PR-04` 表单与 `PR-10` 看板中顺带落地。
- **循环会议 / 系列预订**、**会议室收藏**、**常用参会人组**：均属本系统自身能力，但优先级低于核心闭环。

### 8.4 与第三方依赖的边界复核

| 需求 | 是否依赖第三方 | 说明 |
|---|---|---|
| `PR-01`~`PR-10` | ❌ 否 | 数据全部来自 `users` / `rooms` / `bookings` / `time_slots` / `audit_logs` 等本系统表 |
| `OPT-01`~`OPT-05` | ❌ 否 | 仅涉及本系统配置、代码、DB 约束与测试 |
| 明确不做 | — | 短信/邮件/企微、SSO/AD/HR、日历、IoT、门禁签到、BI 外推、移动端 App、其他项目文档 |

---

## 9. 需求审视：进入设计前必须确认的业务决策（5 维度）

> 按 `.codebuddy/rules/requirement-review.md` 的 5 维度 SOP，以下问题是**本次 15 项需求进入正式 PRD / Schema 设计前的阻塞项**。每条都能用「是/否」或「选项」回答；未确认的规则在本文档中一律标注「待确认」，没有擅自替业务方拍板。

### 维度 1：业务规则

| # | 反问 | 缺失风险 | 影响需求 |
|---|---|---|---|
| 1 | 「最短 15 分钟」与「30 分钟槽位、起止必须对齐」如何统一？**选 A**：起止都对齐且时长为 30 倍数（最短 30min）；**选 B**：允许 15min，末槽按 15min 粒度单独建模；**选 C**：允许任意起止但向上取整到 30min 占用。 | 不澄清则 `BUG-04` 无法修，冲突判定与 `PR-04` 时间轴口径不一致 | `OPT-02`、`PR-04` |
| 2 | 单次预订**最长时长**是否设上限（4h / 8h / 不限）？`end` 是否也受 `now+7天` 约束？ | 不限则可生成上千槽位，`prd.md:117` 的软约束无法落地 | `OPT-02`、`PR-10` |
| 3 | 是否允许**跨天**预订？可预订窗口是否固定 09:00–18:00？ | 影响时间轴渲染、槽位生成、利用率分母 | `PR-04`、`PR-10` |
| 4 | 利用率的分母口径：按 09:00–18:00 × 30min 计算，还是按各会议室自定义可订时段？ | 指标 ≥60% 无法验收 | `PR-10` |
| 5 | `attendeeCount` 与 `attendeeIds` 的关系：人数是否含预订人？不一致时拒绝还是自动修正？ | 影响 `PR-06` 校验与 `PR-09` 容量判断 | `PR-06` |

### 维度 2：边界场景（并发 / 异常 / 容错）

| # | 反问 | 缺失风险 | 影响需求 |
|---|---|---|---|
| 1 | 两个用户同时改期到同一空闲时段，谁成功？是否统一用「先提交者胜 + 唯一约束兜底」？ | 改期可能破坏零超卖 | `PR-07`、`OPT-02` |
| 2 | `PENDING` 预订是否占用槽位？若占用，等待超过 24h 是自动驳回释放，还是仅标记后由人工处理？ | 直接决定 `BUG-02` 的修复方案 | `OPT-02`、`PR-03` |
| 3 | 会议已开始后取消，是释放**剩余**槽位、全部槽位，还是不释放？ | 影响取消后能否被即时重订 | `PR-02`、`OPT-02` |
| 4 | 改期/取消过程中 DB 更新成功但审计写入失败，是否整体回滚？ | 决定「业务成功 = 留痕成功」是否成立 | `PR-02`、`PR-07`、`PR-10` |
| 5 | 时间轴预检显示空闲、提交时已被占用，前端是自动刷新还是仅提示？刷新失败如何降级？ | 影响 `prd.md:18` 冲突报错率指标 | `PR-04`、`OPT-05` |

### 维度 3：数据与状态

| # | 反问 | 缺失风险 | 影响需求 |
|---|---|---|---|
| 1 | `CANCELLED → COMPLETED` 当前被 `schema.md §3.1` 允许，是否改为**禁止**？终态集合到底是哪些？ | 状态机错误会让已取消预订被统计为已完成 | `OPT-02`、`PR-10` |
| 2 | `REJECTED` 后能否用相同参数重订同一时段？是否保留原记录供审计？ | 影响驳回后重订体验与审计口径 | `PR-03` |
| 3 | `MAINTENANCE` 是否可退回 `AVAILABLE`？`DECOMMISSIONED` 是否可逆？ | 影响 `PR-09` 状态机与历史房间展示 | `PR-09` |
| 4 | 软删除记录保留多久？≥1 年的审计日志是「在线可查」还是「归档可恢复」？ | 影响 `PR-10` 查询性能与合规 | `PR-10`、`OPT-05` |
| 5 | `time_slots` 用「物理删除释放」还是「改为有效标记 + 定期清理」？ | 影响 `OPT-02` 的账本设计与唯一键改造 | `OPT-02`、`OPT-05` |

### 维度 4：权限与审计

| # | 反问 | 缺失风险 | 影响需求 |
|---|---|---|---|
| 1 | `approver_id` 到底由谁赋值：被代订人的上级、VIP 会议室管理员、还是固定 `APPROVER` 角色池？`requirement_review.md §6.4` 二选一未定 | `PR-03` 待办列表无法确定收件人 | `PR-03` |
| 2 | 「部门专属」的「本部门」是否含子部门？无部门用户能订哪些房间？ | `OPT-04` 过滤条件无法写 | `OPT-04`、`PR-05` |
| 3 | `PRIVATE` 预订对 `ADMIN` / `AUDITOR` 是否可见（含主题与参会人）？ | 隐私与审计冲突 | `PR-01`、`PR-06`、`PR-10` |
| 4 | `RECEPTIONIST` / `AUDITOR` 两个枚举角色的权限矩阵是什么？`MANAGER` 看板是否只见本部门？ | 角色定义缺失，后台需求无法验收 | `OPT-04`、`PR-10` |
| 5 | token 失效策略选 A（服务端吊销清单）/ B（短 TTL + refresh）/ C（保持 8h 仅前端清）？ | 决定 `OPT-01` logout 实现与安全性 | `OPT-01` |
| 6 | `superior_id` 由谁维护、是否需要界面？在 `PR-08` 授权管理里一并做，还是 `PR-09` 后台做？ | 「上级取消下级」无数据基础 | `PR-02`、`PR-08` |

### 维度 5：集成与通知（只确认边界，不新增第三方需求）

| # | 反问 | 缺失风险 | 影响需求 |
|---|---|---|---|
| 1 | 本项目定位是**培训演示样例**还是**拟上线产品**？两者的验收口径与优先级完全不同（见 §0.4） | 需求优先级可能整体错位 | 全部 15 项 |
| 2 | `prd.md:19` 的「通知到达率 ≥99%」与 US-06 三通道，在本次范围内是**明确移出 v1.0**，还是保留为「后续版本」？ | 上线范围口径失真 | `PR-02`、`PR-03`、`PR-09` |
| 3 | 站内消息中心（不依赖第三方）是否纳入 v1.1？若纳入，`notifications` 表的 `channel` 枚举是否需要扩展 `IN_APP`？ | 表结构可能返工 | §8.3 |
| 4 | `users` / `departments` 在本期确认**自维护、不接 HR/AD**，那么用户入职/离职/调部门的维护入口由谁负责、是否进 `PR-09` 后台？ | 权限与可见性数据会腐化 | `OPT-04`、`PR-09` |
| 5 | `prd.md:178` 的「单时区、服务端 NTP 统一」是否覆盖所有客户端？跨时区员工选 09:00 时以哪个时区为准？ | `api.ts:16` 传 `yyyy-MM-ddTHH:mm:ss` 无 offset，可能产生 8 小时错位 | `PR-04`、`OPT-05` |

---

### 回复指引

请按编号逐条回复业务决策，三种方式皆可：

1. **逐条回复**：`维度1-1: 选 A`、`维度2-2: PENDING 占槽，24h 自动驳回`……
2. **自由描述**：一次性说明口径，由我归并到「业务确认结果」章节。
3. **直接编辑本文档**：在 §9 表格后追加「业务确认结果」，标注确认人/日期。

收齐后我会**追加**「业务确认结果」章节（不覆盖原始反问），标注未确认或自相矛盾的点，并把已确认规则回填到 `PR-*` 的「业务规则」与 AC 中。按 `requirement-review.md §6` 的判定，5 个维度关键问题确认 ≥80% 且矛盾点澄清后，才建议进入正式 PRD / Schema 设计阶段。

---

## 10. 附录：本次审阅依据文件清单

| 类别 | 文件 |
|---|---|
| 产品 | `prd.md`、`requirement_review.md`、`schema.md`、`README.md`（空）、`CLAUDE.md` |
| 文档/测试 | `docs/api.md`、`docs/UNIT_TEST_REPORT.md`、`docs/TEST_REVIEW_BASELINE.md` |
| SQL | `schema.sql`、`seed.sql`、`seed_rooms.sql` |
| 后端关键类 | `BookingController.java`、`BookingServiceImpl.java`、`IdempotencyServiceImpl.java`、`RoomController.java`、`RoomServiceImpl.java`、`AuthController.java`、`AuthInterceptor.java`、`CurrentUser.java`、`TokenService.java`、`WebMvcConfig.java`、`GlobalExceptionHandler.java`、`CreateBookingRequest.java`、`TimeSlotMapper.java`、`DelegationGrantMapper.java` |
| 配置 | `application.yml`、`application-dev.yml`、`application-prod.yml` |
| 前端 | `App.tsx`、`Login.tsx`、`BookingPage.tsx`、`RoomList.tsx`、`api.ts`、`auth.ts`、`test/setup.ts`、`__tests__/*` |
| 规范 | `.codebuddy/rules/requirement-review.md`、`design-doc-template.md`、`java-backend.md`、`react-frontend.md`、`test-review.md`、`ai-collaboration.md` |

> 本报告只做审视与需求建议，**未修改任何源码、Schema 或配置**，也未新增依赖。所有「待验证」项需在真实运行环境与集成测试中确认后再定级。
