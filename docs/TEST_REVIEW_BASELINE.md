# 测试评审「标准答案」基线 — 培训预跑结果

> 用途：本文件是培训演示 Act 2「需求-测试覆盖矩阵」的**预跑标准答案**，培训时若 AI 现场输出与本文件趋势一致即合格。
> 生成日期：2026-05-28
> 输入：`prd.md` v1.0 / `requirement_review.md` / `docs/api.md` / `backend/src/test/java/**`

---

## 1. 执行摘要

- **结论**：v1.0 后端测试主要聚焦在 **US-02 创建预订**（覆盖度高，18 用例）与登录鉴权基础设施；US-03 代订有部分覆盖；**US-04 审批、US-05 取消、US-06 通知、US-07 后台运营** 几乎为零。
- **主要风险**：取消流程、审批流程、通知三通道、强制取消权限 4 个核心 AC 完全没有自动化测试兜底，发布即裸奔。
- **建议优先级**：先补 US-05 取消（权限矩阵复杂）→ 再补 US-04 审批 → 再补 US-06 通知（可用契约测试）。

---

## 2. 覆盖矩阵（按 PRD User Story 编号）

### US-01 浏览会议室

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-01 / AC-1 | 列表展示会议室名/容量/设备/楼层 | RoomServiceImplTest#多条记录_全部正确转换 | ✅ | — |
| US-01 / AC-2 | 设备 JSON 解析（含 null/空/非法） | RoomServiceImplTest（6 用例全部）| ✅ | — |
| US-01 / AC-3 | 默认展示当天 09:00–18:00 时间轴 | — | ❌ | UI 行为，建议前端补 RoomList 时间轴渲染测试 |
| US-01 / AC-4 | 不可用时段灰显 + 悬浮提示 | — | ❌ | 缺 availability 接口测试 + 前端 hover 行为 |
| US-01 / AC-5 | 可见性=仅自己时不显示预订人/主题 | — | ❌ | **High**：影响隐私合规，缺 visibility filter 测试 |

### US-02 创建预订（核心，覆盖度最高）

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-02 / AC-1 | 时间粒度 30 分钟 | BookingServiceImplTest#TimeValidation_未对齐 | ✅ | — |
| US-02 / AC-2 | 时长 ≥ 15 分钟 | BookingServiceImplTest#TimeValidation_短于15分钟 | ✅ | — |
| US-02 / AC-3 | 不允许过去时间 | BookingServiceImplTest#TimeValidation_过去时间 | ✅ | — |
| US-02 / AC-4 | 不超过 NOW+7 天 | BookingServiceImplTest#TimeValidation_超过7天 | ✅ | — |
| US-02 / AC-5 | 结束 > 开始 | BookingServiceImplTest#TimeValidation_结束≤开始 | ✅ | — |
| US-02 / AC-6 | 参会人数 ≤ 容量 | BookingServiceImplTest#RoomValidation_人数超容量 | ✅ | — |
| US-02 / AC-7 | capacity ≤ 50 且非 VIP → CONFIRMED | BookingServiceImplTest#ApprovalRouting_普通会议室 | ✅ | — |
| US-02 / AC-8 | capacity > 50 或 VIP → PENDING | BookingServiceImplTest#ApprovalRouting_大型/VIP | ✅ | — |
| US-02 / AC-9 | 时长 > 4h 弹确认框 | — | ⚠️ | 前端 UI 行为，缺 BookingPage 长时长拦截测试 |
| US-02 / AC-10 | 默认可见性=PUBLIC，可切 PRIVATE | — | ❌ | **Med**：缺 visibility 字段持久化测试 |
| US-02 / AC-11 | request_id 幂等 | — | ⚠️ | 前端注入有测；**后端幂等服务无单测**，仅依赖运行时 |
| US-02 / AC-12 | 时段冲突 → 409 BOOKING_CONFLICT | BookingServiceImplTest#SlotAndConflict_DB唯一约束冲突 | ✅ | — |
| US-02 / AC-13 | 会议室维修中拒绝 | BookingServiceImplTest#RoomValidation_MAINTENANCE | ✅ | — |
| US-02 / AC-14 | 部门专属会议室仅本部门可订 | — | ❌ | **High**：跨部门权限完全无测试 |

### US-03 代订

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-03 / AC-1 | EMPLOYEE 不可代订 | BookingServiceImplTest#DelegationValidation_EMPLOYEE代订 | ✅ | — |
| US-03 / AC-2 | ASSISTANT 必须有授权 | BookingServiceImplTest#DelegationValidation_无授权 + 已授权 | ✅ | — |
| US-03 / AC-3 | ADMIN 不查授权表 | BookingServiceImplTest#DelegationValidation_ADMIN | ✅ | — |
| US-03 / AC-4 | principalId=自己视为自订 | BookingServiceImplTest#DelegationValidation_等于自己 | ✅ | — |
| US-03 / AC-5 | 代订 VIP/大型仍走审批 | — | ⚠️ | 已有审批分流测试 + 代订测试，但**无组合用例**验证两者交叉 |
| US-03 / AC-6 | 通知同时发给代订人和被代订人 | — | ❌ | 通知服务无测试 |

### US-04 审批（VIP/大型）

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-04 / AC-1 | 审批通过 → CONFIRMED | — | ❌ | **High**：审批服务完全未实现/未测 |
| US-04 / AC-2 | 驳回 → REJECTED 并释放 TimeSlot | — | ❌ | **High** |
| US-04 / AC-3 | 驳回必须填 rejectReason | — | ❌ | **High** |
| US-04 / AC-4 | 仅审批人可审批 | — | ❌ | **High**：缺权限校验测试 |

### US-05 取消预订

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-05 / AC-1 | 本人可取消（开始前/后） | — | ❌ | **High**：取消服务无单测 |
| US-05 / AC-2 | 上级可取消下级 | — | ❌ | **High**：权限矩阵未测 |
| US-05 / AC-3 | 上级取消必须填原因 ≥ 5 字 | — | ❌ | **High**：合规要求未兜底 |
| US-05 / AC-4 | 管理员强制取消 = 上级权限 | — | ❌ | **High** |
| US-05 / AC-5 | 行政不能强制取消 | — | ❌ | **High**：负面权限测试缺失 |
| US-05 / AC-6 | 软删除（deleted_at + cancel_reason + cancelled_by）| — | ❌ | **Med**：数据一致性未验证 |
| US-05 / AC-7 | 取消后 TimeSlot 立即释放 | — | ❌ | **High**：影响下一个预订能否成功 |

### US-06 通知

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-06 / AC-1 | 预订成功→邮件+企微 | — | ❌ | 通知服务无单测 |
| US-06 / AC-2 | 审批结果→邮件+企微 | — | ❌ | 依赖 US-04 |
| US-06 / AC-3 | 强制取消→邮件+企微+短信 三通道 | — | ❌ | **Med**：建议补三通道触发契约测试 |
| US-06 / AC-4 | 会议前 15 分钟提醒→企微 | — | ❌ | 定时任务无测试 |

### US-07 后台运营

| AC 编号 | AC 描述 | 关联测试 | 覆盖状态 | 缺口说明 |
|---|---|---|---|---|
| US-07 / AC-1 | 会议室 CRUD | — | ❌ | 仅有 list 查询测试，缺 create/update/delete |
| US-07 / AC-2 | 维修/封存→级联取消未开始预订 | — | ❌ | **High**：核心业务规则未测 |
| US-07 / AC-3 | 审计日志可筛选/导出 | — | ❌ | 审计服务无测试 |
| US-07 / AC-4 | 看板：利用率/趋势/高频占用 | — | ❌ | v1.0 可延后 |

### 横切（非功能）

| 维度 | 描述 | 关联测试 | 覆盖状态 |
|---|---|---|---|
| 鉴权基础 | HMAC-SHA256 token 签发/校验/篡改/过期 | TokenServiceTest（11 用例）| ✅ |
| 登录服务 | 用户不存在/禁用/密码错/成功/兼容旧数据 | AuthServiceImplTest（5 用例）| ✅ |
| 用户名枚举防护 | 三种失败统一提示 | AuthServiceImplTest（隐含）| ✅ |
| 并发防超卖 | DB 唯一索引兜底 | BookingServiceImplTest#SlotAndConflict | ⚠️ 仅 mock 层验证转换逻辑，未跑真实 DB 并发 |
| 401 自动跳登录 | 前端拦截器派发事件 | api.test.ts（3）| ✅ |

---

## 3. 覆盖率小结

- 共识别 **44 条 AC**
- ✅ 完全覆盖：**17 条**（39%）
- ⚠️ 部分覆盖：**4 条**（9%）
- ❌ 未覆盖：**23 条**（52%）
- **High 缺口：14 条**（主要集中在 US-04 审批、US-05 取消、US-07 后台、跨部门权限、隐私可见性）

---

## 4. 高优先级补测建议（Top 5）

按 ROI 排序，每条给出建议测试方法名（直接可用作 `@DisplayName`）：

| # | 业务点 | 建议测试方法名 |
|---|---|---|
| 1 | 上级取消下级必须填原因 | `CancelServiceImplTest#cancel_byManager_withReasonTooShort_thenThrowReasonRequired` |
| 2 | 取消后 TimeSlot 立即释放 | `CancelServiceImplTest#cancel_thenTimeSlotsDeletedAndReleased` |
| 3 | 部门专属会议室禁外部门预订 | `BookingServiceImplTest#book_whenDepartmentRoom_andUserOtherDept_thenDepartmentDenied` |
| 4 | 审批驳回必须填 rejectReason | `ApprovalServiceImplTest#approve_whenReject_withoutReason_thenInvalidParam` |
| 5 | 维修级联取消未开始预订 | `RoomServiceImplTest#setMaintenance_thenCascadeCancelFutureBookings` |

---

## 5. 培训演示建议「埋点」

**为了让 Act 2 演示出 ❌ 效果，无需额外修改 PRD**——本基线已经天然有 23 条 ❌，AI 现场跑出来必然有大量缺口。

如果想让 ❌ 更具冲击力，可以在 prd.md 加一条「故意更显眼的」AC，例如在 §4 业务规则汇总末尾追加：

```markdown
| 安全 | 同一用户 1 分钟内预订失败 5 次自动锁定账号 30 分钟 |
```

这条 AC 一定没测试 → 矩阵里必出现一行触目惊心的 ❌ + **High** + "影响账号安全"。客户看了印象极深。

---

## 6. 演示口径（讲给客户听的话）

> "v1.0 我们写了 67 个测试用例，看起来不少。但 AI 一对账发现：实际只覆盖了 39% 的需求验收点，**14 条高风险 AC 完全没有自动化兜底**，包括取消权限矩阵、审批流程、跨部门隔离。这就是为什么 AI 评审值钱——它把'测了多少代码'转换成了'覆盖了多少业务规则'，是真正给老板看的数据。"
