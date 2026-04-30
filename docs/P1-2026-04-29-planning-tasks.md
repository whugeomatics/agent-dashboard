# P1 Planning Tasks

## 目标

本文件只管理 P1 需求和规划阶段要完成的事情。完成这些任务后，后续 agent 才应进入代码实现。

## 成功标准

规划阶段完成时应满足：

- P1 MVP 范围清楚。
- Codex 日志字段和统计口径有待验证清单。
- Dashboard 页面和 API 的最小契约清楚。
- 多 agent 分工清楚。
- 验收标准可执行。
- AGENTS.md 已更新当前阶段重点。

## 任务拆分

### 1. 需求基线

产出：

- `docs/P1-2026-04-29-requirements.md`

验证：

- 文档说明用户目标、P1 范围、禁止事项、核心指标和成功标准。
- 不包含 Claude Code、Cursor、本地网关的实现任务。

### 2. Codex 日志调研计划

产出：

- `docs/research/P1-2026-04-29-codex-log-research.md`

验证：

- 明确需要验证的日志路径、事件类型、字段和样例。
- 明确不能记录 prompt/response 正文。
- 明确哪些字段是真实值，哪些字段是推断值。

### 3. API Contract 草案

产出：

- `docs/contracts/P1-2026-04-29-report-api.md`

验证：

- 定义 `/api/report` 的查询参数。
- 定义 `range`、`summary`、`daily`、`models`、`sessions` 字段。
- 定义字段类型、排序、空数据行为。

### 4. Dashboard 信息架构

产出：

- `docs/P1-2026-04-29-dashboard-ia.md`

验证：

- 明确首页模块顺序。
- 明确每个模块依赖的 API 字段。
- 不设计营销页、登录页、云同步页。

### 5. 开发任务清单

产出：

- `docs/milestones/P1-codex-dashboard/P1-2026-04-29-tasks.md`

验证：

- 拆分 Research、Backend、Frontend、Review、Acceptance 的工作。
- 每个任务都有可验证输出。
- 不把 P2+ 工作混进 P1。

### 6. 验收标准

产出：

- `docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md`

验证：

- 包含启动验证。
- 包含真实日志读取验证。
- 包含统计一致性验证。
- 包含隐私和只读验证。
- 包含未验证项记录模板。

### 7. AGENTS.md 更新

产出：

- `AGENTS.md`

验证：

- 当前阶段聚焦 P1 规划和 Codex MVP。
- 明确开发前必须先补齐 research、contract、tasks、acceptance 文档。
- 保留不提前实现 Claude Code、Cursor、本地网关的约束。

## 当前建议执行顺序

```text
1. requirements -> 验证：范围和成功标准明确
2. codex log research plan -> 验证：可开始字段实测
3. report api contract -> 验证：前后端接口一致
4. dashboard IA -> 验证：页面只消费 contract 字段
5. milestone tasks -> 验证：agent 可按文档分工
6. acceptance template -> 验证：开发后能闭环验收
7. AGENTS.md -> 验证：长期协作规则更新
```

## 暂不处理的事项

- Claude Code 具体接入。
- Cursor 具体接入。
- 本地模型网关实现。
- SQLite schema。
- provider adapter。
- 费用估算。
- 安装包或桌面壳。

