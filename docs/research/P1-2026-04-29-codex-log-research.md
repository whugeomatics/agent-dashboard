# P1 Codex Log Research Plan

## 目标

验证 Codex 本地 session JSONL 日志是否足够支撑 P1 Dashboard MVP。

本文件包含调研计划和 2026-04-29 的本机实测结果。后续 Research Agent 如继续补充样例字段，不能粘贴 prompt 或 response 正文。

## 需要验证的问题

### 1. 日志位置

待验证：

- Windows 下 Codex session JSONL 的实际路径。
- 是否存在按日期或项目分目录。
- 文件名是否包含 session id 或时间信息。

验收：

- 文档记录实际路径模式。
- 文档说明 dashboard 是否需要用户手动配置路径。

### 2. 事件类型

待验证：

- 是否存在 `token_count` 事件。
- 是否存在 `turn_context` 或等价事件。
- token 事件是否带 timestamp。
- 同一个 session 中 model 信息出现的频率。

验收：

- 列出 P1 必须读取的事件类型。
- 列出可忽略的事件类型。

### 3. Usage 字段

待验证：

- `last_token_usage.input_tokens`
- `last_token_usage.cached_input_tokens`
- `last_token_usage.output_tokens`
- `last_token_usage.reasoning_output_tokens`
- `last_token_usage.total_tokens`

关键判断：

- `last_token_usage` 是否是增量值。
- 是否还有累计字段。
- 哪些字段可能缺失。

验收：

- 明确 P1 聚合使用累计 usage 快照 delta。
- 如果字段缺失，定义默认值或跳过策略。

### 4. Model 归因

待验证：

- `turn_context.model` 是否能关联到同 session 后续 token 事件。
- 一个 session 中是否可能切换 model。
- token 事件自身是否带 model。

验收：

- 明确 model 优先级：token 事件自身 model、同 session 最近一次 `turn_context.model`、`unknown`。

### 5. 时间口径

待验证：

- timestamp 字段格式。
- timestamp 时区。
- 是否能转换为本地日期。
- session active time 的开始和结束事件。

验收：

- 明确 daily 聚合使用本地时区。
- 明确 active time 是估算值。

## 隐私边界

调研时允许读取 metadata，不允许写入 `.codex`。

禁止记录到文档：

- prompt 正文。
- response 正文。
- 用户源码片段。
- API key。
- 敏感路径细节。

如果需要样例，只保留字段结构和脱敏数值。

## 调研输出模板

```text
## 实测结果

- Codex 日志路径：
- 已验证文件数量：
- 已验证时间范围：
- 已验证事件类型：
- usage 字段结论：
- model 归因结论：
- timestamp 结论：
- 不能确认的问题：
```

## 实测结果

- Codex 日志路径：`%USERPROFILE%\.codex\sessions\YYYY\MM\DD\rollout-*.jsonl`。
- 已验证文件数量：17 个 JSONL 文件，其中抽样解析最近 8 个文件。
- 已验证时间范围：2026-04-27 到 2026-04-29。
- 顶层字段：`timestamp`、`type`、`payload`。
- timestamp 格式：原始 JSON 为 UTC ISO 字符串，例如 `2026-04-29T12:47:11.459Z`。
- 已验证事件类型：`session_meta`、`turn_context`、`event_msg`、`response_item`、`compacted`。
- `event_msg.payload.type` 中存在 `token_count`，抽样 8 个文件中发现 724 条 `token_count`。
- `session_meta.payload.id` 可作为 session id；文件名也包含 rollout id，但 P1 应优先使用 payload id。
- `turn_context.payload.model` 可用于模型归因；抽样中出现 `gpt-5.5` 和 `codex-auto-review`。
- `turn_context.payload.timezone` 存在，抽样值为 `Asia/Shanghai`；daily 聚合应转换成本地日期。

## Usage 字段实测

`token_count` 事件路径：

```text
type = event_msg
payload.type = token_count
payload.info.last_token_usage
payload.info.total_token_usage
```

已验证字段：

- `input_tokens`
- `cached_input_tokens`
- `output_tokens`
- `reasoning_output_tokens`
- `total_tokens`

同时存在：

- `payload.info.model_context_window`
- `payload.info.rate_limits`

P1 不需要读取 rate limit 字段。

## 关键发现：不能直接累加 last_token_usage

实测发现同一 session 中会连续出现相同 usage 快照。如果直接累加每条 `last_token_usage`，会重复计数。

抽样结果：

```text
File 1:
- token_count events: 45
- changed cumulative snapshots: 23
- duplicate snapshots: 22
- sum(last_token_usage.total_tokens): 2,430,790
- delta(total_token_usage.total_tokens): 1,250,343
- final total_token_usage.total_tokens: 1,250,343

File 2:
- token_count events: 29
- changed cumulative snapshots: 15
- duplicate snapshots: 14
- sum(last_token_usage.total_tokens): 1,185,156
- delta(total_token_usage.total_tokens): 614,623
- final total_token_usage.total_tokens: 614,623

File 3:
- token_count events: 85
- changed cumulative snapshots: 41
- duplicate snapshots: 44
- sum(last_token_usage.total_tokens): 4,168,237
- delta(total_token_usage.total_tokens): 1,999,275
- final total_token_usage.total_tokens: 1,999,275
```

P1 统计口径应改为：

- 按 session、timestamp 顺序读取 `token_count`。
- 使用 `payload.info.total_token_usage` 作为累计快照。
- 对相邻累计快照做非负 delta。
- 只聚合 delta 大于 0 的变化。
- 相同累计快照视为重复快照，跳过。
- `last_token_usage` 只作为辅助校验字段，不作为主聚合来源。

如果累计字段缺失，再考虑使用 `last_token_usage` 加去重策略，但必须在验收报告记录风险。

## Model 归因结论

P1 model 优先级：

1. token 事件自身 model，如果未来版本提供。
2. 同 session 中当前 token 事件之前最近一次 `turn_context.payload.model`。
3. `session_meta.payload.model_provider` 只能标识 provider，不能替代 model。
4. 无法归因时使用 `unknown`。

注意：

- 一个 session 理论上可能出现多个 `turn_context.payload.model`。
- P1 聚合时应允许一个 session 对应多个 model。

## 时间结论

- 原始 `timestamp` 是 UTC ISO 字符串。
- P1 daily 聚合应转换为本地时区日期。
- 可优先使用 `turn_context.payload.timezone`；缺失时使用系统本地时区。
- session active time 使用该 session 内第一个有效 token delta 到最后一个有效 token delta 的时间跨度。

## P1 开发建议

Backend 实现时建议把解析分成三层：

1. JSONL event reader：只读文件，输出脱敏事件 metadata。
2. Usage normalizer：把累计快照转换成 delta usage event。
3. Report aggregator：按日期、模型、session 聚合 delta event。

验收时必须增加一项：

- 验证直接累加 `last_token_usage` 与累计 delta 的差异，确认实现没有采用会重复计数的口径。

## P1 阻塞判断

如果满足以下条件，可以进入 P1 开发：

- 能稳定定位 Codex JSONL 文件。
- 至少一个真实 session 中存在 token usage。
- 能从 usage 中得到 input、cached input、output、reasoning output 或合理默认值。
- 能把 token 事件归到 session。
- 能把 token 事件归到日期。

如果 model 归因不稳定，不阻塞 MVP，但 dashboard 必须展示 `unknown` 并在验收报告记录限制。
