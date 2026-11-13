# `.codebuddy/rules/` 使用说明

本目录是项目的**细化 AI 协作规则**，配合根目录的 `CLAUDE.md` 使用。

---

## 1. 文件清单与用途

| 文件 | 用途 | 何时加载 |
|---|---|---|
| `requirement-review.md` | 需求审视 SOP（5 维度反问） | 用户给出原始需求 / 说"审视/review" |
| `design-doc-template.md` | PRD / 技术方案 / Schema / API / ADR 模板 | 用户说"生成 PRD/方案/schema" |
| `java-backend.md` | Spring Boot 3 + MyBatis-Plus 编码规范 | 修改 `backend/**/*.java` |
| `react-frontend.md` | React 18 + TS + Vite 编码规范 | 修改 `frontend/**/*.{ts,tsx}` |
| `microservice.md` | API 契约、可观测性、容错 | 涉及服务间调用、运维 |
| `ai-collaboration.md` | 4 阶段协作流程 + 修改话术库 | 用户说"修改/调整/调优"，开发者引导 AI 时参考 |

---

## 2. 加载机制

### 2.1 CodeBuddy

每个文件头部 frontmatter：

```yaml
---
description: <一句话说明>
globs: <匹配路径>
alwaysApply: false
---
```

- `alwaysApply: true` → 总是加载（仅 `CLAUDE.md` 用）
- `globs: backend/**/*.java` → 命中文件路径时自动加载
- 仅 `description` → AI 根据用户意图按需加载

### 2.2 Claude Code / Cursor 兼容

- Claude Code：建议复制本目录到 `.claude/rules/`
- Cursor：复制到 `.cursor/rules/`，并将 `CLAUDE.md` 复制为 `.cursorrules`

---

## 3. 使用流程示例

### 场景 A：新需求来了

```
开发者："做一个 X 功能"
  ↓
AI 自动加载 requirement-review.md
  ↓
AI 输出 5 维度反问清单
  ↓
开发者逐条回答
  ↓
AI 沉淀业务决策到 docs/{feature}_review.md
```

### 场景 B：进入设计

```
开发者："基于决策生成 PRD"
  ↓
AI 自动加载 design-doc-template.md
  ↓
AI 按 PRD 模板输出，每条追溯业务决策
  ↓
开发者审阅 → 迭代到定稿
```

### 场景 C：开始编码

```
开发者："实现 US-02 创建预订接口"
  ↓
AI 自动加载 java-backend.md（因为要改 backend/**）
  ↓
AI 先复述理解 + 列计划
  ↓
开发者确认 → AI 编码 + 单测
```

### 场景 D：调优修改

```
开发者：[FACT/INTENT/EXAMPLE 话术] 描述问题
  ↓
AI 加载 ai-collaboration.md
  ↓
AI 复述理解 + 列假设 + 列范围
  ↓
等确认 → 修改 → 输出 diff 摘要
```

---

## 4. 维护规则

### 4.1 何时新增规则

- 团队反复出现同一类问题（说明缺规则）
- 新引入技术栈 / 框架
- 业务出现新模式（如新引入审批流引擎）

### 4.2 何时修改规则

- 实践中发现规则与现实冲突
- 技术栈版本升级
- 团队最佳实践演进

### 4.3 修改流程

1. 在 PR 中明确写"修改 rule，理由 X"
2. 团队 review（rules 是团队公约，不是个人偏好）
3. 合并后**全员 onboarding**（同步告知）

---

## 5. 与 `CLAUDE.md` 的关系

```
CLAUDE.md              ← 总纲（永远加载，浓缩精华）
  │
  ├─ .codebuddy/rules/  ← 细则（按需加载，详尽具体）
  │   ├─ 流程类：requirement-review / design-doc / ai-collaboration
  │   └─ 编码类：java-backend / react-frontend / microservice
  │
  └─ docs/              ← 项目实际产出（决策记录、PRD、Schema）
```

**冲突时优先级**：`CLAUDE.md` > `.codebuddy/rules/` > AI 自身知识。

---

## 6. 推广到其他项目

本目录可作为**新项目模板**直接拷贝，需要做的修改：

1. `CLAUDE.md` 第 1 节"项目概述" → 改为新项目信息
2. `java-backend.md` / `react-frontend.md` → 按新项目栈调整版本
3. `requirement-review.md` / `design-doc-template.md` / `ai-collaboration.md` → **基本不用改**（流程类规则通用）

---

## 7. 反馈与改进

发现 rules 不工作（比如 AI 还是自由发挥）时：

1. 检查 `description` / `globs` 是否准确
2. 检查 rules 内容是否**够具体**（"做好"是没用的，"用 X 不用 Y"才有用）
3. 检查是否与 `CLAUDE.md` 冲突
4. 必要时在 `ai-collaboration.md` 第 5 节用"刹车话术"主动拉回 AI
