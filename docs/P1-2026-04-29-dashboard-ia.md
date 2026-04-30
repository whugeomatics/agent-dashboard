# P1 Dashboard Information Architecture

## 目标

定义 P1 Dashboard 首页的信息结构。首页就是实际统计工具，不做 landing page。

## 页面原则

- 信息密度优先，适合开发者每天查看。
- 所有展示字段来自 `/api/report`。
- 前端不重新定义 token 统计公式。
- 不展示 prompt 或 response 正文。
- 不设计登录、云同步、配置向导。

## 页面结构

### 1. 顶部工具栏

内容：

- 页面标题：Codex Usage Dashboard。
- 时间窗口切换：Today、7D、30D、This Month。
- 数据刷新按钮。

依赖 API：

- `range.kind`
- `range.start_date`
- `range.end_date`
- `range.timezone`

### 2. Summary 指标区

展示：

- Total Tokens。
- Net Usage。
- Cached Input。
- Cache Hit Rate。

依赖 API：

- `summary.total_tokens`
- `summary.net_tokens`
- `summary.cached_input_tokens`
- `summary.cache_hit_rate`

说明：

- 指标标题可以简短，但 tooltip 或辅助文案应能解释口径。
- 不显示费用估算。

### 3. Daily Usage 趋势

展示：

- 按日期的 token 柱状图或折线图。
- 至少支持 total tokens。
- 如图表复杂度可控，可拆分 input/output/reasoning。

依赖 API：

- `daily[].date`
- `daily[].total_tokens`
- `daily[].input_tokens`
- `daily[].cached_input_tokens`
- `daily[].output_tokens`
- `daily[].reasoning_output_tokens`
- `daily[].net_tokens`

### 4. Model Usage 表

展示列：

- Model。
- Total Tokens。
- Input。
- Cached Input。
- Output。
- Reasoning Output。
- Net Usage。
- Cache Hit Rate。
- Sessions。
- Active Time。

依赖 API：

- `models[]`

排序：

- 默认与 API 一致，按 total tokens 降序。

### 5. Session Usage 表

展示列：

- Session。
- Started At。
- Active Time。
- Models。
- Total Tokens。
- Net Usage。
- Input。
- Output。
- Reasoning Output。

依赖 API：

- `sessions[]`

排序：

- 默认与 API 一致，按 started_at 降序。

## 空态

当 API 返回空数据：

- Summary 显示 0。
- Daily 区域显示无数据状态。
- Models 和 Sessions 表显示空态。
- 不把空数据视为错误。

## 错误态

当 API 返回错误或无法读取日志：

- 页面显示简短错误。
- 不展示 stack trace。
- 不展示本机敏感路径的完整细节，除非用户主动打开调试信息。

## 后续阶段再做

- 自定义日志路径配置。
- SQLite 导入状态。
- provider/tool 过滤。
- 费用估算。
- 模型价格表。
- 自然周统计。
- 导出 CSV。

