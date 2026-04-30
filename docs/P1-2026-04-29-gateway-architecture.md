# Local Model Gateway Architecture

## 目标

长期版本要从“读取日志”升级为“本地模型网关”。只有请求经过本地网关，才能稳定统计所有工具、所有 provider、所有模型的 token 和耗时。

## 核心模块

```text
Tool Adapter
    |
    v
Gateway API
    |
    v
Router
    |
    v
Provider Adapter
    |
    v
Remote Model API

Usage Recorder <------ Gateway API / Router / Provider Adapter
       |
       v
SQLite
       |
       v
Dashboard API
       |
       v
Dashboard UI
```

## 模块职责

### Tool Adapter

负责不同工具的接入说明和配置。

例子：

- Codex。
- Claude Code。
- Cursor。
- OpenAI SDK。
- 自研脚本。

注意：工具适配优先通过配置完成，不优先做侵入式插件。

### Gateway API

暴露本地 HTTP API。

第一优先级：

- `/v1/chat/completions`
- `/v1/responses`

后续再考虑：

- Anthropic-compatible endpoint。
- Gemini-compatible endpoint。
- 管理 API。

### Router

决定请求应该转发给哪个 provider 和 model。

职责：

- 根据 model 名选择 provider。
- 根据配置做 model alias。
- 后续支持 fallback。
- 后续支持预算限制。

### Provider Adapter

屏蔽不同 provider 的请求和响应差异。

每个 adapter 需要实现：

- request transform。
- response transform。
- streaming transform。
- usage extraction。
- error normalization。

### Usage Recorder

记录标准 usage event。

必须记录：

- request id。
- tool。
- provider。
- model。
- session id。
- started at。
- ended at。
- status。
- input tokens。
- cached input tokens。
- output tokens。
- reasoning output tokens。
- total tokens。

可选记录：

- first token latency。
- stream duration。
- estimated cost。
- error code。

默认不记录：

- prompt 正文。
- response 正文。
- 源码上下文。

## 数据流

```text
1. Tool sends request to localhost gateway.
2. Gateway assigns request id.
3. Router resolves provider and model.
4. Provider adapter forwards request.
5. Gateway streams or returns response.
6. Usage recorder writes normalized usage event.
7. Dashboard reads SQLite through local API.
```

## 为什么不能只靠日志

日志型统计适合 MVP，但长期有边界：

- 不同工具日志格式不一致。
- 有些工具不暴露 token。
- 很难精确统计 latency。
- 很难统一 provider 和 model 命名。
- 无法实现预算、限流、fallback。

网关型统计能解决这些问题，但要求工具支持 base URL、代理或 provider 配置。

## 隐私策略

默认策略：

- usage 数据本地存储。
- 不记录 prompt 和 response 正文。
- API key 只保存在本机。
- dashboard 只绑定 `127.0.0.1`。

后续如果支持 prompt 采样，必须单独开关，默认关闭。
