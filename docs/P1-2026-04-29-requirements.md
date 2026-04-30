# P1 Requirements: Codex Usage Dashboard MVP

## 背景

项目目标是做一个本地 agent/model usage dashboard，先解决“Codex 每次会话和不同时间窗口的 token 消耗不可见”的问题。

长期方向会扩展到 Claude Code、Cursor、OpenAI SDK、自研脚本和本地模型网关，但 P1 不实现这些能力。

## 当前阶段判断

当前处于阶段 0 / 阶段 1 之间：

- 阶段 0 负责确认 Codex 本地日志是否可用、字段是否稳定、统计口径是否成立。
- 阶段 1 负责基于 Codex 日志做一个只读 dashboard MVP。

本轮工作只做需求和规划，不进入功能开发。

## 用户目标

用户希望看到：

- 每次 Codex 会话消耗了多少 token。
- 每天、每周、每月的 token 消耗。
- 不同模型的 token 消耗。
- 输入、缓存输入、输出、推理输出 token 的拆分。
- 会话级、模型级、日期级的排行或趋势。

## P1 MVP 范围

P1 只支持 Codex。

允许做：

- 读取本机 Codex session JSONL 日志。
- 从日志中提取 usage metadata。
- 按本地日期聚合今日、7 天、30 天、本月数据。
- 按 session 聚合 token 消耗。
- 按 model 聚合 token 消耗。
- 展示 dashboard 页面。
- 提供 dashboard 所需的本地 API。

不做：

- 不接入 Claude Code。
- 不接入 Cursor。
- 不实现本地模型网关。
- 不代理 Codex 请求。
- 不修改 `.codex` 目录下任何文件。
- 不记录 prompt 正文。
- 不记录 response 正文。
- 不做登录、云同步、账户体系。
- 不做精确费用结算。

## 核心指标

P1 必须明确展示或可计算：

- `input_tokens`
- `cached_input_tokens`
- `output_tokens`
- `reasoning_output_tokens`
- `total_tokens`
- `non_cached_input_tokens = input_tokens - cached_input_tokens`
- `net_tokens = non_cached_input_tokens + output_tokens`
- `cache_hit_rate = cached_input_tokens / input_tokens`

## 时间窗口

P1 需要支持：

- 今日。
- 最近 7 天。
- 最近 30 天。
- 当前自然月。

按周统计在 P1 中可以通过最近 7 天满足，不需要额外引入复杂自然周逻辑。自然周可以作为 P2 或后续增强。

## 数据来源实测结论

已在本机 Codex 日志中验证：

- Codex session JSONL 路径为 `%USERPROFILE%\.codex\sessions\YYYY\MM\DD\rollout-*.jsonl`。
- `token_count` 事件存在于 `type=event_msg` 且 `payload.type=token_count` 的记录。
- usage 字段存在于 `payload.info.last_token_usage` 和 `payload.info.total_token_usage`。
- `turn_context.payload.model` 可用于同一 session 内的模型归因。
- 原始 timestamp 是 UTC ISO 字符串，可转换为本地日期。
- `last_token_usage` 会随重复快照重复出现，不能直接逐条累加。

## 统计口径

P1 默认口径：

- token 数据来自 `token_count` 事件。
- 主聚合口径使用 `payload.info.total_token_usage` 的相邻累计快照 delta。
- 相同累计快照视为重复快照，跳过。
- `last_token_usage` 只作为辅助校验字段，不作为主聚合来源。
- 如果 token 事件缺少 model，则使用同 session 最近的 `turn_context.model` 推断。
- session active time 使用同 session 第一个 token 事件到最后一个 token 事件的时间跨度。
- model active time 使用同 model 第一个 token 事件到最后一个 token 事件的时间跨度。

限制：

- active time 是日志跨度估算，不是 API latency。
- 真实 request latency、first token latency、stream duration 等到本地网关阶段再实现。

## 初始页面需求

Dashboard 首页就是使用统计页面，不做营销页。

页面至少包含：

- 时间窗口切换。
- 总 token 卡片。
- net usage 卡片。
- cached input 卡片。
- cache hit rate 卡片。
- 每日 usage 趋势。
- 模型统计表。
- 会话统计表。

页面字段必须来自 API contract 或服务端聚合结果，前端不重新定义统计口径。

## 初始 API 需求

P1 可以先使用一个查询接口：

```text
GET /api/report?days=7
GET /api/report?days=30
GET /api/report?month=2026-04
```

返回结构：

```json
{
  "range": {},
  "summary": {},
  "daily": [],
  "models": [],
  "sessions": []
}
```

开发前需要补充 API contract，至少明确每个字段名称、类型、含义和排序规则。

## 隐私要求

P1 默认只读、只统计 metadata。

必须遵守：

- 不写入 `.codex`。
- 不复制 prompt 正文。
- 不复制 response 正文。
- 文档、日志、验收报告中不要粘贴敏感 prompt 或代码上下文。

## P1 成功标准

规划阶段成功标准：

- 本文档明确 P1 的范围、指标、约束。
- roadmap、MVP、风险和协作文档互相一致。
- 下一阶段开发前有清晰任务拆分和验收标准。

MVP 开发成功标准：

- dashboard 可本地启动。
- 能读取真实 Codex 日志。
- 今日、7 天、30 天、本月统计可用。
- summary、daily、models、sessions 的统计口径一致。
- 不修改 Codex 原始日志。
- 验收报告记录通过项、失败项和未验证项。
