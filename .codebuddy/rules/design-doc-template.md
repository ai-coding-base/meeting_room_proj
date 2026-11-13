---
description: 标准化设计文档模板。当用户要求生成 PRD、技术方案、Schema、API 设计、ADR 时加载。
globs:
alwaysApply: false
---

# 设计文档标准模板

> 输出位置：`docs/` 目录
> 命名规范：小写下划线，如 `prd.md`、`tech_design.md`、`api.md`、`adr/0001-xxx.md`

---

## 1. PRD 模板（`docs/prd.md`）

```markdown
# {功能名} PRD v{version}

> 版本：v1.0
> 日期：YYYY-MM-DD
> 依据：requirement_review.md 第 X 章业务决策

## 1. 产品目标
- 一句话目标
- 成功指标（可量化）

## 2. 用户角色
| 角色 | 描述 | 关键权限 |

## 3. 核心用户故事
### US-01 {标题}
> 作为 {角色}，我希望 {动作}，以便 {价值}。

**验收标准**
- 行为 1
- 行为 2

## 4. 业务规则汇总
| 类别 | 规则 |

## 5. 关键流程图
（ASCII 流程图）

## 6. 非功能性需求
| 项 | 要求 |

## 7. 范围与不做的事（Out of Scope）
✅ In scope
❌ Out of scope（理由 + 延后版本）
```

**质量要求**：
- 每个 User Story 必须有**可验收的**标准（不是"完成预订"，而是"提交后 200ms 内返回成功，并发送邮件通知"）
- 业务规则必须**可追溯到** `requirement_review.md` 的某条
- 非功能需求必须有**数字**（QPS、延迟、可用性）

---

## 2. 技术方案模板（`docs/tech_design.md`）

```markdown
# {功能名} 技术方案 v{version}

## 1. 背景与目标
- 业务背景（1 段）
- 技术目标（解决什么问题）
- 非目标（明确不做什么）

## 2. 架构概览
- 系统上下文图（C4 Level 1）
- 容器图（C4 Level 2）

## 3. 关键设计决策
### 决策 1：{标题}
- **方案 A**：...（优劣）
- **方案 B**：...（优劣）
- **选择**：A，理由 {...}
- **风险**：{...}

## 4. 核心数据模型
（引用 schema.md，不重复）

## 5. 核心流程时序
（Mermaid 时序图）

## 6. 容错与降级
| 场景 | 策略 |

## 7. 性能与容量
- QPS 估算
- 数据量增长
- 索引/缓存策略

## 8. 安全与合规
- 认证授权
- 数据脱敏
- 审计

## 9. 可观测性
- 日志关键点
- 指标
- 告警

## 10. 上线方案
- 发布顺序
- 回滚策略
- 灰度计划

## 11. 工作量评估
| 模块 | 人天 |
```

---

## 3. DB Schema 模板（`docs/schema.md` + `docs/schema.sql`）

### `schema.sql` 结构

```sql
-- ========================================
-- {功能名} v{version} Schema
-- ========================================

-- 表 1：xxx
CREATE TABLE `xxx` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  -- 字段必带注释
  `field_a` VARCHAR(64) NOT NULL COMMENT '{含义}',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,  -- 软删除统一字段名
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_xxx` (`xxx`),
  KEY `idx_xxx` (`xxx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表说明';
```

**强制约束**：
- 所有表 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
- 所有表必带 `id`、`created_at`、`updated_at`
- 软删除用 `deleted_at`（统一名称）
- 字段必带 COMMENT
- 唯一索引前缀 `uk_`，普通索引 `idx_`
- 时间字段一律 `DATETIME`，不用 `TIMESTAMP`

### `schema.md` 结构

```markdown
# DB Schema 设计说明

## 1. 表清单
| 表名 | 用途 | 关键索引 |

## 2. 关键设计决策
### 2.1 {决策点}
- 思路
- 优劣
- 副作用

## 3. 状态机映射
（每个 status 字段的流转）

## 4. 性能与索引
| 高频查询 | 命中索引 |

## 5. 容量估算
（一年/三年数据量）

## 6. 后续优化
（v1.1+ 计划）
```

---

## 4. API 设计模板（`docs/api.md`）

```markdown
# {功能名} API 设计 v{version}

## 通用约定
- BaseURL: /api/v1
- 认证: Bearer Token
- 必带 Header: X-Request-Id

## 接口列表
| # | Method | Path | 描述 | 关联 US |
|---|---|---|---|---|
| 1 | POST | /api/bookings | 创建预订 | US-02 |

## 接口详情

### 1. 创建预订
- **Path**: `POST /api/bookings`
- **Auth**: 必需
- **幂等**: 是（X-Request-Id）

**Request**
```json
{
  "roomId": 1,
  "subject": "周会",
  "startTime": "2026-05-23T14:00:00",
  "endTime": "2026-05-23T15:00:00",
  "attendeeCount": 5,
  "visibility": "PUBLIC"
}
```

**Response 200**
```json
{
  "code": 0,
  "data": { "id": 42, "status": "CONFIRMED" }
}
```

**错误码**
| code | HTTP | 说明 |
| BOOKING_CONFLICT | 409 | 时段已被预订 |
| INVALID_TIME    | 400 | 时间参数非法 |
| ROOM_NOT_AVAILABLE | 409 | 会议室维修中 |
```

---

## 5. ADR 模板（`docs/adr/NNNN-{title}.md`）

> ADR = Architecture Decision Record，记录每一个**有取舍**的架构决策。

```markdown
# ADR-{N}: {决策标题}

- 日期：YYYY-MM-DD
- 状态：Proposed / Accepted / Deprecated / Superseded by ADR-{M}
- 决策人：

## 背景
{为什么需要做这个决策？业务/技术驱动？}

## 备选方案
### 方案 A
- 优点
- 缺点
- 成本

### 方案 B
...

## 决策
**选择方案 X**，理由：
1. ...
2. ...

## 后果
- 正面：
- 负面：
- 缓解：

## 相关
- PRD: docs/prd.md US-XX
- 替代/被替代：ADR-XX
```

---

## 6. AI 输出设计文档时的 Checklist

- [ ] 文档放在了 `docs/` 下，命名符合规范？
- [ ] 引用了 `requirement_review.md` 的业务决策？
- [ ] 每个 User Story 有可验收标准？
- [ ] DB Schema 字段有 COMMENT、表有索引？
- [ ] API 列出了所有错误码与 HTTP 状态码？
- [ ] 关键决策记录为 ADR？
- [ ] 没编造未确认的业务规则？
