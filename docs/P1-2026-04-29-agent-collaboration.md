# Multi-Agent Collaboration

## 目标

后续开发可以借助 harness 思想：多个 agent 并行工作，但它们之间不靠聊天上下文传递关键事实，而是通过文档交互。

核心要求：

- 先设计。
- 再开发。
- 再 review。
- 最后验收。

## 角色

### Planner Agent

职责：

- 拆阶段。
- 定义范围。
- 写设计文档。
- 识别风险和未知点。

主要产出：

- roadmap。
- milestone design。
- acceptance criteria。

### Research Agent

职责：

- 调研工具和 provider 的接入方式。
- 验证配置能力。
- 写调研报告。

主要产出：

- `docs/research/*.md`

### Backend Agent

职责：

- 实现日志解析。
- 实现 API。
- 实现数据库。
- 实现本地网关。

主要产出：

- 后端代码。
- API 文档。
- 自测报告。

### Frontend Agent

职责：

- 实现 dashboard。
- 保证页面信息密度和可读性。
- 保证移动端基本可用。

主要产出：

- 前端代码。
- 页面截图或验收说明。

### Review Agent

职责：

- 只做 code review 和 design review。
- 优先找 bug、统计口径错误、隐私风险、验收缺口。

主要产出：

- `docs/reviews/*.md`

### Acceptance Agent

职责：

- 按验收标准跑检查。
- 记录通过项和失败项。

主要产出：

- `docs/acceptance/*.md`

## 文档交互规则

每个 milestone 至少包含：

```text
docs/milestones/<name>/design.md
docs/milestones/<name>/tasks.md
docs/milestones/<name>/review.md
docs/milestones/<name>/acceptance.md
```

开发前：

- Planner Agent 写 `design.md`。
- 所有 agent 读 `design.md`。
- 不清楚的地方写到 `questions.md`。

开发中：

- Backend 和 Frontend 只改自己负责的文件。
- 如果需要改接口，先改 `design.md` 或 `contracts/*.md`。
- 不通过聊天口头修改契约。

开发后：

- Review Agent 写 `review.md`。
- Backend / Frontend 根据 review 修复。
- Acceptance Agent 写 `acceptance.md`。

## Review 策略

每次 review 至少检查：

- 统计口径是否正确。
- 是否重复计数。
- 是否混淆 total 和增量 token。
- 是否记录了敏感 prompt。
- 是否有清晰错误处理。
- dashboard 是否和 API 字段一致。
- 是否有验收报告。

## 多模型互相 review

可以让不同模型承担不同 review 视角：

- 强代码模型：检查实现 bug 和边界条件。
- 强推理模型：检查架构和统计口径。
- 快速模型：检查文档一致性和遗漏项。

所有 review 结果必须沉淀为文档，不以聊天结论为准。
