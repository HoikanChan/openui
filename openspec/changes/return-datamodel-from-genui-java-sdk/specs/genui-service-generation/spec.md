## MODIFIED Requirements

### Requirement: Streaming generation endpoint
GenUI Service SHALL 通过 `POST /v1/generate` 接收生成请求（prompt 必填，extensionId、dataModel、extraRules、promptOverride 可选），先经 SDK 完成 prompt 拼装，再调用 LLM，并以 `text/event-stream` SSE 方式流式返回 `RenderStreamEnvelope` JSON。响应流的首帧 SHALL 是 `type=dataModel`，content 为请求 dataModel 或 `{}`；后续模型输出 SHALL 使用 `type=dsl`；流结束 SHALL 使用 `type=done`。prompt 为空 SHALL 返回 400。该端点为手写实现，在 Swagger 契约中仅作文档声明。

#### Scenario: 流式生成成功
- **WHEN** 提交合法生成请求
- **THEN** 响应 Content-Type 是 `text/event-stream`
- **AND** 响应流先返回 `dataModel` envelope，再逐段返回 `dsl` envelope，最后返回 `done` envelope

#### Scenario: 拼装上下文生效
- **WHEN** 生成请求携带已注册扩展的 extensionId
- **THEN** 发给 LLM 的 system prompt 含该扩展的组件与工具描述

#### Scenario: 空 prompt 被拒绝
- **WHEN** 生成请求的 prompt 为空或仅空白
- **THEN** 返回 400 与错误说明

### Requirement: LLM invocation behavior parity with retired Node server
GenUI Service 的 LLM 调用配置 SHALL 与被替换的 Node 版对齐：通过环境变量 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 配置 OpenAI 兼容接口，并支持 `HTTPS_PROXY` 与 `NO_PROXY`（含域名后缀 bypass 判断）。流开始前失败 SHALL 返回 502 JSON 错误；流中途失败或 `finish_reason` 非 `stop` SHALL 通过 `type=error` envelope 表达错误，并随后发送 `type=done` envelope 后关闭流。

#### Scenario: 异常收尾返回 error envelope
- **WHEN** LLM 流以 `finish_reason=length` 或连接中断结束
- **THEN** 响应流末尾包含 `type=error` envelope
- **AND** 随后包含 `type=done` envelope

#### Scenario: 流前失败返回 502
- **WHEN** LLM 接口在产生首个 SSE envelope 前返回错误
- **THEN** 服务返回 502 与 JSON 错误体，而非空流

#### Scenario: 代理 bypass 生效
- **WHEN** 设置了 `HTTPS_PROXY` 且 `LLM_BASE_URL` 主机命中 `NO_PROXY` 条目
- **THEN** LLM 请求直连，不经过代理
