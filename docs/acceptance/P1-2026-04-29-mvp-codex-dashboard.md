# P1 MVP Acceptance Template

## 目标

记录 Codex Dashboard MVP 的验收结果。开发完成后由 Acceptance Agent 填写。

## 测试环境

- OS：
- 时区：
- Node/Java/Python 版本：
- 启动命令：
- Dashboard URL：
- Codex 日志路径：
- 验收日期：

## 验收项

### 1. 启动验证

结果：未验证

检查：

- 本地服务可以启动。
- 启动命令写入文档。
- 页面可以打开。

### 2. Codex 日志读取

结果：未验证

检查：

- 能定位 Codex session JSONL。
- 能读取至少一个真实 session。
- 不修改 `.codex` 下任何文件。

### 3. Usage 字段解析

结果：未验证

检查：

- 能解析 input tokens。
- 能解析 cached input tokens。
- 能解析 output tokens。
- 能解析 reasoning output tokens。
- 能解析 total tokens。

### 4. 统计一致性

结果：未验证

检查：

- summary 等于 daily 之和。
- model totals 与 summary 可解释。
- session totals 与 summary 可解释。
- 未重复统计同一 token event。
- 未直接累加每条 `last_token_usage`。
- 已跳过相同 `total_token_usage` 累计快照。

### 5. 时间窗口

结果：未验证

检查：

- Today 可用。
- 7D 可用。
- 30D 可用。
- This Month 可用。
- 日期使用本地时区。

### 6. 页面展示

结果：未验证

检查：

- Summary 指标可见。
- Daily usage 可见。
- Model usage 表可见。
- Session usage 表可见。
- 空态可用。
- 错误态可用。

### 7. 隐私和范围

结果：未验证

检查：

- 不展示 prompt 正文。
- 不展示 response 正文。
- 不写 `.codex`。
- 不实现 Claude Code。
- 不实现 Cursor。
- 不实现本地网关。
- 不实现 SQLite。

## 结论

状态：未验收

可选状态：

- 通过。
- 有条件通过。
- 不通过。

结论说明：

```text
通过项：
失败项：
未验证项：
下一步建议：
```
