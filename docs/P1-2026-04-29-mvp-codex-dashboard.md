# MVP：Codex Usage Dashboard

## 目标

先做一个只读 Codex dashboard，让你能看到本机 Codex 的 token 使用情况。

这个阶段的价值是快速验证：

- Codex 本地日志是否足够稳定。
- token 统计口径是否符合日常观察。
- dashboard 是否真的能解决“看不见用量”的问题。

## 假设

- Codex 会话日志存在于本机 `.codex/sessions`。
- `token_count` 事件里的 `total_token_usage` 是累计快照；相邻累计快照 delta 是 P1 主统计来源。
- `last_token_usage` 会重复出现，不能直接逐条累加。
- 同一会话中的 `turn_context.model` 可用于模型归因。
- Codex MVP 不需要接管 API 请求，因此无法精确知道每次真实 API latency。

## 数据口径

字段：

- `total_tokens`: 总 token。
- `input_tokens`: 输入 token，包含 cached input。
- `cached_input_tokens`: 缓存命中的输入 token。
- `output_tokens`: 输出 token。
- `reasoning_output_tokens`: 推理输出 token。
- `non_cached_input_tokens`: `input_tokens - cached_input_tokens`。
- `net_tokens`: `non_cached_input_tokens + output_tokens`。
- `cache_hit_rate`: `cached_input_tokens / input_tokens`。

时间：

- `daily`: 按本地时区日期聚合。
- `session active time`: 同一 session 内第一次 token 事件到最后一次 token 事件的跨度。
- `model active time`: 同一 model 下第一次 token 事件到最后一次 token 事件的跨度。

事件口径：

- 读取 `type=event_msg` 且 `payload.type=token_count` 的事件。
- 使用 `payload.info.total_token_usage` 计算相邻快照 delta。
- 跳过相同累计快照，避免重复计数。
- `payload.info.last_token_usage` 只用于辅助校验，不作为主聚合来源。

注意：

- MVP 的 active time 是日志跨度估算，不是模型真实推理耗时。
- 真实 latency、first token time、stream duration 要到本地网关阶段才能做准。

## 页面范围

首页展示：

- 今日、7 天、30 天、本月切换。
- 总 token。
- net usage。
- cached input。
- cache hit rate。
- 每日用量柱状图。
- 按模型统计表。
- 按会话统计表。

暂不做：

- 登录。
- 云同步。
- prompt 内容查看。
- 费用精确计算。
- 多 provider 接入。
- 配置 Codex 模型。

## API 范围

第一版只需要一个查询接口：

```text
GET /api/report?days=7
GET /api/report?days=30
GET /api/report?month=2026-04
```

返回：

```json
{
  "range": {},
  "summary": {},
  "daily": [],
  "models": [],
  "sessions": []
}
```

## 开发任务

1. 解析 Codex JSONL 日志。
   验证：能从真实日志读出 token_count 和 model。

2. 聚合 usage。
   验证：同一时间范围内 summary 等于 daily 之和。

3. 提供本地 API。
   验证：浏览器访问 `/api/report?days=7` 返回 JSON。

4. 做 dashboard 页面。
   验证：页面能显示 API 返回的数据。

5. 写验收报告。
   验证：记录本机测试数据、已知限制、下一步建议。

## 验收标准

- 启动命令明确。
- 页面能展示真实 Codex 数据。
- 今日、7 天、30 天、本月切换正常。
- 模型排行和会话排行可见。
- 文档说明每个指标的统计口径。
- 没有修改 `.codex` 内的任何文件。
