# P2 Codex SQLite Acceptance

## 目标

记录 P2 SQLite 持久化与增量采集的验收结果。

## 测试环境

- OS：
- 时区：
- Java 版本：
- Maven 命令：
- SQLite 文件：
- Codex 日志路径：
- 验收日期：

## 验收项

### 1. 构建验证

结果：未验证

检查：

- `mvn -DskipTests clean package` 通过。
- 生成 `target\agent-dashboard-0.1.0-SNAPSHOT.jar`。

### 2. Schema 初始化

结果：未验证

检查：

- SQLite 文件可创建。
- `schema_migrations` 存在。
- `source_files` 存在。
- `usage_events` 存在。
- 索引存在。

### 3. 首次 ingestion

结果：未验证

检查：

- `--ingest` 可扫描真实 Codex JSONL。
- 输出 `status=ok`。
- `events_inserted > 0`，如果本机没有 usage 日志则记录为空数据原因。
- `source_files` checkpoint 被写入。

### 4. 重复 ingestion

结果：未验证

检查：

- 第二次执行 `--ingest`。
- 不重复插入相同 usage event。
- `events_inserted=0` 或只包含真实新增事件。

### 5. 增量 ingestion

结果：未验证

检查：

- 新增 JSONL 行后，只导入新增 delta。
- checkpoint 正确推进。
- 文件变小或替换时不重复统计历史 event。

### 6. Report API 兼容

结果：未验证

检查：

- `/api/report?days=7` 返回 P1 contract 字段。
- summary 与 daily 聚合一致。
- models 和 sessions 可解释。
- 空数据返回 200 和空数组/零值。

### 7. 隐私和范围

结果：未验证

检查：

- SQLite 不包含 prompt 正文。
- SQLite 不包含 response 正文。
- 不写 `.codex`。
- 不实现 Claude Code。
- 不实现 Cursor。
- 不实现本地网关。

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
