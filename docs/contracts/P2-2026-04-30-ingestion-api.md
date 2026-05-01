# P2 Ingestion Contract

## 目标

定义 P2 增量采集行为。P2 采集只面向 Codex session JSONL，并把 usage delta 写入 SQLite。

## CLI

P2 增加一个本地 CLI 模式：

```text
java -jar target\agent-dashboard-0.1.0-SNAPSHOT.jar --ingest
```

可选参数：

```text
--sessions-dir=<path>
--db=<path>
--timezone=<iana-zone>
```

环境变量：

- `CODEX_SESSIONS_DIR`: 覆盖 Codex session JSONL 目录。
- `AGENT_DASHBOARD_DB`: 覆盖 SQLite 文件。
- `DASHBOARD_TIMEZONE`: 覆盖统计时区。

## HTTP

P2 暂不要求新增公开 ingestion HTTP API。

原因：

- P2 是本地工具，CLI 更容易验收。
- 避免在 dashboard 页面引入写操作。
- 降低误触发重扫风险。

后续如需要手动刷新，可在 P2 review 后决定是否增加只绑定 `127.0.0.1` 的内部 endpoint。

## 采集输入

默认扫描：

```text
%USERPROFILE%\.codex\sessions\YYYY\MM\DD\rollout-*.jsonl
```

只读取：

- `session_meta`
- `turn_context`
- `event_msg` 且 `payload.type=token_count`

忽略：

- prompt 正文。
- response 正文。
- `response_item` 内容。
- rate limit 字段。

## 增量规则

每次 ingestion：

1. 扫描 `CODEX_SESSIONS_DIR` 下的 `.jsonl` 文件。
2. 在 `source_files` 中查找文件 checkpoint。
3. 如果文件未见过，从第 1 行开始读。
4. 如果文件已见过且 `size_bytes`、`modified_at`、`file_fingerprint` 均未变化，跳过该文件。
5. 如果文件新增、变大、变小或 fingerprint 明显变化，视为 changed source file，重新读取该文件并依赖 `usage_events.event_key` 防止重复写入。
6. 每成功处理一行，更新内存 checkpoint。
7. 文件处理成功后，更新 `source_files.last_line`、`size_bytes`、`modified_at`、`scanned_at`。

说明：

- P2 的 token delta 依赖同一 session 内上一条 cumulative snapshot。
- 当前 schema 不持久化上一条 cumulative snapshot，因此 changed source file 必须从头重放，才能避免只读 `last_line + 1` 时把第一条新增 snapshot 错算为全量 delta。
- P2 仍然只处理 changed source files；未变化文件不重读。

## Delta 规则

P2 继续沿用 P1 统计口径：

- 主来源是 `payload.info.total_token_usage`。
- 同一 session 内按 timestamp/line order 对相邻累计快照做非负 delta。
- 相同累计快照跳过。
- `last_token_usage` 不作为主聚合来源。

P2 持久化的是 delta event，不是 cumulative snapshot。

## 去重规则

`usage_events.event_key` 必须稳定。

建议组成：

```text
codex|<session_id>|<source_path>|<line_number>|<total_tokens>|<input_tokens>|<output_tokens>
```

如果后续发现 Codex 日志 line_number 不稳定，应在 P2 review 中记录并调整 contract。

## 错误处理

单行 JSON 解析失败：

- 跳过该行。
- 更新 `source_files.status=error`。
- `last_error` 记录错误类型和行号，不记录原始行内容。

单文件失败：

- 不影响其他文件。
- 已成功写入的 usage event 保留。
- checkpoint 只推进到最后成功处理行。

数据库写入失败：

- ingestion 返回非 0 退出码。
- 不更新该文件 checkpoint 到失败行之后。

## 输出

成功输出 JSON：

```json
{
  "status": "ok",
  "files_scanned": 0,
  "files_changed": 0,
  "events_inserted": 0,
  "events_skipped": 0,
  "errors": []
}
```

失败输出 JSON：

```json
{
  "status": "error",
  "files_scanned": 0,
  "files_changed": 0,
  "events_inserted": 0,
  "events_skipped": 0,
  "errors": [
    {
      "path": "redacted-or-safe-path",
      "line": 0,
      "message": "parse error"
    }
  ]
}
```

错误信息不得包含 prompt、response 或原始 JSONL 行内容。
