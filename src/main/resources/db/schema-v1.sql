-- name: create_schema_migrations
CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

-- name: create_source_files
CREATE TABLE IF NOT EXISTS source_files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tool TEXT NOT NULL,
  path TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  modified_at TEXT NOT NULL,
  last_line INTEGER NOT NULL,
  last_event_timestamp TEXT,
  file_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL,
  last_error TEXT,
  scanned_at TEXT NOT NULL,
  UNIQUE(tool, path)
);

-- name: create_usage_events
CREATE TABLE IF NOT EXISTS usage_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source_file_id INTEGER NOT NULL,
  line_number INTEGER NOT NULL,
  event_key TEXT NOT NULL,
  tool TEXT NOT NULL,
  session_id TEXT NOT NULL,
  model TEXT NOT NULL,
  event_timestamp TEXT NOT NULL,
  local_date TEXT NOT NULL,
  input_tokens INTEGER NOT NULL,
  cached_input_tokens INTEGER NOT NULL,
  output_tokens INTEGER NOT NULL,
  reasoning_output_tokens INTEGER NOT NULL,
  total_tokens INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(source_file_id) REFERENCES source_files(id),
  UNIQUE(event_key)
);

-- name: create_idx_usage_events_local_date
CREATE INDEX IF NOT EXISTS idx_usage_events_local_date ON usage_events(local_date);

-- name: create_idx_usage_events_model
CREATE INDEX IF NOT EXISTS idx_usage_events_model ON usage_events(model);

-- name: create_idx_usage_events_session
CREATE INDEX IF NOT EXISTS idx_usage_events_session ON usage_events(session_id);

-- name: create_idx_usage_events_timestamp
CREATE INDEX IF NOT EXISTS idx_usage_events_timestamp ON usage_events(event_timestamp);

-- name: insert_schema_migration
INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, ?);

-- name: find_source_file
SELECT id, size_bytes, modified_at, file_fingerprint
FROM source_files
WHERE tool = ? AND path = ?;

-- name: upsert_source_file
INSERT INTO source_files(tool, path, size_bytes, modified_at, last_line, last_event_timestamp,
  file_fingerprint, status, last_error, scanned_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(tool, path) DO UPDATE SET
  size_bytes = excluded.size_bytes,
  modified_at = excluded.modified_at,
  last_line = excluded.last_line,
  last_event_timestamp = excluded.last_event_timestamp,
  file_fingerprint = excluded.file_fingerprint,
  status = excluded.status,
  last_error = excluded.last_error,
  scanned_at = excluded.scanned_at;

-- name: insert_usage_event
INSERT OR IGNORE INTO usage_events(source_file_id, line_number, event_key, tool, session_id,
  model, event_timestamp, local_date, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: load_usage_events
SELECT session_id, model, event_timestamp, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens
FROM usage_events
WHERE local_date >= ? AND local_date <= ?
ORDER BY event_timestamp, id;
