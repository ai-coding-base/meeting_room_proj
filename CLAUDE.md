# 项目 AI 协作契约（CLAUDE.md）

> 本文件是项目的**全局 AI 协作规范**，所有 AI 助手在本仓库工作时必须遵守。
> 团队成员（人类）也应阅读，因为它定义了"我们如何与 AI 配合"。
>
> 版本：v1.0 · 最后更新：2026-05-23

---

## 1. 项目概述（必须先读）

| 项 | 值 |
|---|---|
| **项目名** | 会议室预订系统（Meeting Room Booking） |
| **业务目标** | 公司内部会议室自助预订、审批、取消、通知闭环 |
| **当前阶段** | v1.0 设计期 → 开发期 |
| **核心文档** | `requirement_review.md` / `prd.md` / `schema.md` |

**任何修改前，AI 必须先确认是否已阅读上述核心文档。**

---

## 2. 技术栈与版本（硬约束）

### 2.1 后端
- **语言**：Java 17（LTS）
- **框架**：Spring Boot 3.2.x + Spring Web + Spring Validation
- **持久层**：MyBatis-Plus 3.5.x（不使用 JPA）
- **数据库**：MySQL 8.0+ / utf8mb4
- **缓存**：Redis 7.x
- **构建**：Maven 3.9+
- **测试**：JUnit 5 + Mockito + Testcontainers

### 2.2 前端
- **语言**：TypeScript 5.x（严格模式 `strict: true`）
- **框架**：React 18 + Vite 5
- **路由**：React Router 6
- **状态管理**：Zustand（轻量本地状态）+ TanStack Query（服务端状态）
- **UI 组件**：Ant Design 5
- **表单**：React Hook Form + Zod
- **测试**：Vitest + React Testing Library

### 2.3 微服务/部署
- **服务通信**：REST（对外） + 内部按需 gRPC
- **可观测性**：OpenTelemetry + Prometheus + Grafana
- **网关**：Spring Cloud Gateway（如需）

> ⚠️ **不允许**未经讨论引入新依赖。新增依赖前必须在 PR 描述中说明理由。

---

## 3. AI 协作四阶段流程

> 这是本项目最重要的协作约定。每个任务必须明确处于哪个阶段，AI 行为随之切换。

### 阶段一：需求审视（Requirement Review）
**触发词**：`审视需求` / `review requirement` / 用户给出原始需求时
**AI 行为**：
- 加载 `.codebuddy/rules/requirement-review.md`
- 按 5 维度（业务规则 / 边界场景 / 数据状态 / 权限审计 / 集成通知）输出反问清单
- **禁止**直接进入编码

### 阶段二：方案设计（Design）
**触发词**：`生成 PRD` / `设计 schema` / `技术方案`
**AI 行为**：
- 加载 `.codebuddy/rules/design-doc-template.md`
- 按模板输出 PRD / Schema / API / 技术方案
- 每个决策必须**追溯到需求审视的某条业务确认**
- **禁止**写代码

### 阶段三：编码实现（Implementation）
**触发词**：`实现` / `开发` / `写代码`
**AI 行为**：
- 加载对应的 `.codebuddy/rules/java-backend.md` 或 `react-frontend.md`
- 编码前必须复述："我将修改 X 文件实现 Y 功能，依赖 Z"，等待确认或自行推进
- 严格遵守编码规范，不得自由发挥
- 每个公开方法必须有单元测试

### 阶段四：调优修正（Refinement）
**触发词**：`修改` / `调整` / `不对` / `这里有问题`
**AI 行为**：
- 加载 `.codebuddy/rules/ai-collaboration.md`
- **必须**先复述当前理解 + 列出修改假设
- 不假设不修改邻近无关代码（外科手术原则）
- 修改后输出 diff 摘要

---

## 4. 核心原则（Karpathy 四条 + 项目补充）

### 4.1 Think Before Coding
不要假设。不确定就问。多解读列出来让用户选。

### 4.2 Simplicity First
写最少能解决问题的代码。不投机。不做未要求的功能。

### 4.3 Surgical Changes
只动必须动的地方。一致性 > 个人偏好。不要顺手"改进"邻近代码。

### 4.4 Goal-Driven Execution
任务必须可验证。先定义"成功标准"（测试 / 检查点），再循环迭代直到通过。

### 4.5 项目补充：可追溯性（Traceability）
任何**业务决策**必须能追溯到 `requirement_review.md` 第 6 章的某条；
任何**API 设计**必须能追溯到 `prd.md` 的某个 User Story。

---

## 5. 目录结构约定

```
meeting_room_proj/
├── CLAUDE.md                       ← 本文件
├── .codebuddy/
│   └── rules/                      ← 细化规则（按需加载）
├── docs/
│   ├── prd.md                      ← 产品需求
│   ├── requirement_review.md       ← 需求审视记录
│   ├── schema.md / schema.sql      ← 数据库设计
│   ├── api.md                      ← API 设计
│   └── adr/                        ← 架构决策记录
├── backend/                        ← Spring Boot 后端
│   ├── src/main/java/com/company/booking/
│   │   ├── controller/             ← HTTP 入口
│   │   ├── service/                ← 业务逻辑
│   │   ├── mapper/                 ← MyBatis-Plus Mapper
│   │   ├── domain/                 ← 领域模型 / Entity
│   │   ├── dto/                    ← 请求/响应对象
│   │   ├── config/                 ← 配置
│   │   └── common/                 ← 通用工具/异常/拦截器
│   └── src/test/java/...
└── frontend/                       ← React 前端
    ├── src/
    │   ├── pages/                  ← 路由级页面
    │   ├── components/             ← 复用组件
    │   ├── hooks/                  ← 自定义 hooks
    │   ├── api/                    ← 接口封装（基于 TanStack Query）
    │   ├── stores/                 ← Zustand stores
    │   ├── types/                  ← TS 类型定义
    │   └── utils/
    └── tests/
```

---

## 6. 通用编码红线（任何语言）

| # | 红线 | 说明 |
|---|---|---|
| 1 | **禁止 SQL 字符串拼接** | 100% 参数化查询；动态字段用白名单校验 |
| 2 | **禁止硬编码密钥/密码** | 配置走环境变量 / 配置中心 |
| 3 | **禁止裸捕获异常** | `catch (Exception e)` 必须记录上下文，禁止吞异常 |
| 4 | **禁止 println / console.log 调试代码进主干** | 用统一日志框架（SLF4J / 自封装 logger） |
| 5 | **禁止超过 800 行的文件 / 80 行的方法** | 强制拆分 |
| 6 | **禁止未审查的"灵活性"** | 没人要求的可配置项一律不加 |
| 7 | **禁止删除已有 dead code** | 即使你认为没用；只能提醒，不能动 |
| 8 | **修改后必须自检：每行变更能追溯到用户的直接请求吗？** | 如不能，回退 |

---

## 7. AI 主动行为契约

AI 在以下情况**必须主动**：
- ✅ 发现需求自相矛盾 → **停下来提问**
- ✅ 发现安全漏洞（SQL 注入 / 越权 / 明文密码）→ **拒绝实现并说明**
- ✅ 发现新增依赖 → **先列出来等确认**
- ✅ 发现要修改 100 行以上 → **先列计划等确认**
- ✅ 单元测试失败 → **先告知，不擅自修改测试让其通过**

AI 在以下情况**禁止主动**：
- ❌ 自由"重构"现有代码风格
- ❌ 自由删除"看起来没用"的代码
- ❌ 自由升级依赖版本
- ❌ 自由添加未要求的功能 / 配置项 / 错误处理

---

## 8. 提交规范

```
<type>(<scope>): <subject>

<body>

Refs: #<issue> / docs: <relative-path>
```

`type`: feat / fix / refactor / docs / test / chore
`scope`: backend / frontend / db / docs / ci

**示例**：
```
feat(backend): 实现预订创建接口（含幂等校验）

- 新增 BookingController.create
- 新增 IdempotencyKey 表与 Mapper
- TimeSlot 唯一约束兜底超卖

Refs: docs/prd.md US-02
```

---

## 9. 进入项目前的 AI Checklist

每次 AI 接到新任务，先在心里（或显式）回答：

- [ ] 当前任务处于哪个阶段（需求 / 设计 / 编码 / 调优）？
- [ ] 是否已读取 `requirement_review.md` / `prd.md`？
- [ ] 是否已加载对应的 `.codebuddy/rules/*.md`？
- [ ] 我的输出能否追溯到某条业务确认 / 某个 User Story？
- [ ] 我有没有做用户没要求的事？

---

> 如本文件与某条 `.codebuddy/rules/*.md` 冲突，**以本文件为准**。
> 如本文件不足以支撑某场景，**先提问，再扩充本文件**，不要自由发挥。
