# AGENTS.md

本文件是后续 agent 进入项目后的当前阶段工作手册。

维护原则：

- 只展开当前阶段必须遵守的任务、约束、必读文档和验收门槛。
- 已完成阶段只保留状态和文档索引，不在本文件堆积执行细节。
- 每个阶段都应形成符合该阶段目标的 `AGENTS.md`。
- 在一个阶段周期内，根目录 `AGENTS.md` 可以随着设计、开发、review、验收发现动态调整。
- 阶段结束时，除完成该阶段所有设计、contract、任务、review、acceptance 文档外，还必须把该阶段最终版 `AGENTS.md` 按 `docs/archive/P<阶段>-YYYY-MM-DD-AGENTS.md` 归档。
- 每个阶段完成后，把长期有效的新约定补回本文件；阶段细节沉淀到对应 docs。

## 1. 项目目标

本项目最终目标是做一款本地模型使用统计与路由工具。

阶段路线：

1. P0：可行性调研。
2. P1：Codex Dashboard MVP。
3. P2：SQLite 持久化与增量采集。
4. P3：OpenAI-compatible 本地模型网关。
5. P4：多 provider 适配。
6. P5：Codex、Claude Code、Cursor 等工具接入。
7. P6：产品化。

长期能力目标：

- 每次会话 token 消耗。
- 每个模型 token 消耗。
- 每个模型使用时长。
- 每天、每周、每月用量。
- provider、tool、model 维度排行。

## 2. 当前阶段

当前阶段：P3 设计准备。

P1 和 P2 已通过验收。下一步只聚焦 P3：OpenAI-compatible 本地模型网关的设计、contract、任务拆分和验收标准。P3 仍然遵守“先设计、再开发、文档落地”的方针；在 P3 设计和 contract 补齐前，不应直接实现网关代码。

当前阶段目标：

1. 明确本地网关的最小 MVP 边界。
2. 设计 OpenAI-compatible endpoint contract。
3. 设计 provider adapter contract。
4. 设计 usage event contract，复用 P2 SQLite 统计能力。
5. 明确流式响应、错误记录、token usage 记录和隐私边界。
6. 拆分 P3 开发任务和验收标准。

当前阶段禁止：

- 不直接实现网关代码，直到 P3 设计、contract、tasks、acceptance 文档补齐。
- 不接入 Claude Code、Cursor。
- 不实现多 provider 自动路由。
- 不引入登录、云同步、计费系统。
- 不记录 prompt 正文。
- 不记录 response 正文。
- 不破坏 P1/P2 `/api/report` contract。

## 3. 当前阶段必读文档

P3 设计前，后续 agent 至少应先读：

- `docs/archive/P1-2026-04-29-AGENTS.md`
- `docs/archive/P2-2026-04-30-AGENTS.md`
- `docs/P2-2026-04-30-README.md`
- `docs/contracts/P2-2026-04-30-database-schema.md`
- `docs/contracts/P2-2026-04-30-ingestion-api.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-design.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-implementation-plan.md`
- `docs/reviews/P2-2026-04-30-codex-sqlite-review.md`
- `docs/acceptance/P2-2026-04-30-codex-sqlite.md`
- `docs/contracts/P1-2026-04-29-report-api.md`
- `docs/research/P1-2026-04-29-codex-log-research.md`

## 4. P3 文档待办

P3 开发前必须先补齐：

- `docs/P3-2026-04-30-README.md`
- `docs/contracts/P3-2026-04-30-openai-compatible-gateway.md`
- `docs/contracts/P3-2026-04-30-provider-adapter.md`
- `docs/contracts/P3-2026-04-30-usage-event.md`
- `docs/milestones/P3-local-gateway/P3-2026-04-30-design.md`
- `docs/milestones/P3-local-gateway/P3-2026-04-30-tasks.md`
- `docs/acceptance/P3-2026-04-30-local-gateway.md`

## 5. P3 验收门槛

P3 不能只以“服务能启动”为完成标准。验收至少覆盖：

- 网关默认只绑定 `127.0.0.1`。
- OpenAI-compatible chat completions 非流式请求可代理并返回兼容响应。
- 如实现 streaming，必须保持 SSE chunk 兼容并记录 stream duration。
- usage event 写入 SQLite，且能被 `/api/report` 聚合。
- 失败请求记录 status、耗时和错误类型。
- 不存 prompt/response 正文。
- 不破坏 P2 ingestion。
- 构建通过。
- smoke test 或等价端到端验证通过。
- review 和 acceptance 文档已更新。

## 6. 已完成阶段索引

### P1：Codex Dashboard MVP

状态：通过。

验证记录：

- Maven package：用户真实终端执行 `mvn -DskipTests clean package`，结果 `BUILD SUCCESS`。
- smoke test：用户真实终端执行 `powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1`，结果 `P1 smoke test passed`。

文档索引：

- `docs/archive/P1-2026-04-29-AGENTS.md`
- `docs/P1-2026-04-29-README.md`
- `docs/P1-2026-04-29-roadmap.md`
- `docs/P1-2026-04-29-requirements.md`
- `docs/research/P1-2026-04-29-codex-log-research.md`
- `docs/contracts/P1-2026-04-29-report-api.md`
- `docs/milestones/P1-codex-dashboard/P1-2026-04-29-backend-prototype.md`
- `docs/reviews/P1-2026-04-30-codex-dashboard-review.md`
- `docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md`

### P2：SQLite 持久化与增量采集

状态：通过。

验证记录：

- Maven package：用户真实终端执行 `mvn -DskipTests clean package`，结果 `BUILD SUCCESS`。
- smoke test：用户真实终端执行 `powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1`，结果 `P2 smoke test passed`。

文档索引：

- `docs/archive/P2-2026-04-30-AGENTS.md`
- `docs/P2-2026-04-30-README.md`
- `docs/contracts/P2-2026-04-30-database-schema.md`
- `docs/contracts/P2-2026-04-30-ingestion-api.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-design.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-tasks.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-implementation-plan.md`
- `docs/reviews/P2-2026-04-30-codex-sqlite-review.md`
- `docs/acceptance/P2-2026-04-30-codex-sqlite.md`

P2 长期保留约定：

- SQLite 默认路径为 `%USERPROFILE%\.agent-dashboard\agent-dashboard.sqlite`，可由 `AGENT_DASHBOARD_DB` 覆盖。
- SQLite 访问统一使用 `org.xerial:sqlite-jdbc` 和 JDBC API；业务层不得直接创建数据库连接。
- 所有 SQLite DDL/DML 必须集中在 `src/main/resources/db/*.sql`，Java 代码只读取命名 SQL 并执行，不内联建表或查询 SQL。
- P2 ingestion 通过 CLI `--ingest` 触发。
- usage event 持久化 delta，不持久化 cumulative snapshot。
- changed source file 从头重放并依赖 `event_key` 去重，避免缺少上一条 cumulative snapshot 时错算 delta。
- 不存 prompt/response 正文。
- `/api/report` contract 继续兼容 P1。

## 7. 通用工作原则

### 7.1 先设计，再开发，再验收

每个阶段按顺序推进：

1. 更新当前阶段工作手册 `AGENTS.md`。
2. 写设计文档。
3. 写 contract。
4. 写任务拆分。
5. 开发实现。
6. 做 review。
7. 写验收报告。
8. 阶段结束时归档最终版 `AGENTS.md`。
9. 把长期约定保留在下一阶段工作手册中。

没有设计和 contract 时，不应直接开发。

### 7.2 阶段聚焦

每个阶段只解决当前阶段的问题。如果发现后续阶段需要的能力，只记录到文档，不提前实现。

### 7.3 代码结构边界

前后端必须保持文件级拆分：

- 前端静态页面和脚本放在 `src/main/resources/static/`。
- HTTP/API 层只处理路由、参数、状态码和 response。
- ingestion 层只处理日志读取和 usage event 生成。
- repository 层只处理 JDBC、SQL 执行和数据库映射。
- report 层只处理统计聚合和 contract 输出。

禁止把入口、前端 HTML、SQL、HTTP handler、ingestion 和 report 聚合长期堆在一个 Java 类里。

### 7.4 文档是 agent 之间的接口

多个 agent 协作时，不依赖聊天上下文传递关键约定。关键事实必须写入文档。

阶段性文档命名格式：

```text
P<阶段号>-YYYY-MM-DD-<主题>.md
```

`README` 必须带阶段和日期归档；阶段结束后的 `AGENTS` 最终版必须进入 `docs/archive/`。

### 7.5 Review Checklist

每次提交或阶段验收前至少检查：

- 是否偏离当前阶段目标。
- 是否新增了未要求的功能。
- 是否提前实现后续阶段。
- 是否可能重复统计 token。
- 是否混淆增量 usage 和累计 usage。
- 是否记录了敏感 prompt 或 response。
- 是否把 SQL 内联进 Java 代码。
- 是否把前端页面重新塞回 Java 字符串。
- 是否更新了对应文档。
- 是否有验收标准和验收结果。

## 8. Agent 分工

Planner Agent：

- 拆阶段。
- 定义边界。
- 写设计文档。
- 写验收标准。
- 识别风险和未知点。

Research Agent：

- 调研工具日志、字段、provider API。
- 输出到 `docs/research/*.md`。

Backend Agent：

- 实现日志解析、API、SQLite ingestion、本地网关。
- 开发前必须确认对应 contract。
- 不默认记录 prompt 或 response 正文。

Frontend Agent：

- 实现 dashboard。
- 页面字段必须来自 API contract。
- 不在前端重新定义统计口径。

Review Agent：

- 检查 bug、统计口径、隐私风险、重复计数、边界条件。
- 输出到 `docs/reviews/*.md` 或 milestone review。

Acceptance Agent：

- 按验收标准跑检查。
- 记录通过项、失败项、未验证项。
- 输出到 `docs/acceptance/*.md` 或 milestone acceptance。

## 9. Windows 构建执行约定

本项目在 Codex 沙箱中执行 Windows `.cmd`、`cmd.exe /c` 或本地 Java 服务启动时，可能遇到沙箱进程权限限制。

处理规则：

- Maven 构建以用户真实 Windows 终端执行结果为准。
- 首选构建命令：`mvn -DskipTests clean package`。
- 项目约定构建命令也可写为：`cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package`。
- 如果 Codex 沙箱无法运行上述命令，应在验收文档中记录为“沙箱执行受限”，不要判断为代码或 Maven 失败。
- 当用户提供真实终端截图或输出显示 `BUILD SUCCESS` 时，可把 Maven package 验收项记录为通过，并注明验证来源是用户真实终端。
- 需要启动本地 HTTP 服务或 smoke test 时，如果沙箱无法启动进程，应请求用户在真实终端执行，并把结果补回 acceptance 文档。

## 10. 变更本文件的规则

当出现以下情况时，应更新本文件：

- 当前阶段变化。
- 阶段边界变化。
- 统计口径变化。
- 新增长期协作约定。
- 验收发现必须长期遵守的规则。
- 文档命名、归档或阶段编号规则变化。

更新规则：

- 阶段进行中可以直接调整根目录 `AGENTS.md`，但只写当前阶段真正需要的规则。
- 阶段结束时，必须把该阶段最终版 `AGENTS.md` 归档为 `docs/archive/P<阶段>-YYYY-MM-DD-AGENTS.md`。
- 开始下一阶段前，必须确认上一阶段最终版 `AGENTS.md` 已归档。
- 已完成阶段细节应放入对应 docs，只在本文件保留索引。
