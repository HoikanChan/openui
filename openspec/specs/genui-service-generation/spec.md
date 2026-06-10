## ADDED Requirements

### Requirement: Streaming generation endpoint
GenUI Service SHALL 通过 `POST /v1/generate` 接收生成请求（prompt 必填，contextId、dataModel、tools、extraRules 可选），先经 SDK 完成 prompt 拼装，再调用 LLM，并以 `text/plain; charset=utf-8` chunked 方式流式返回生成的 `openui-lang` 文本。prompt 为空 SHALL 返回 400。该端点为手写实现，在 Swagger 契约中仅作文档声明。

#### Scenario: 流式生成成功
- **WHEN** 提交合法生成请求
- **THEN** 响应以 chunked 文本流逐段返回 `openui-lang`，客户端可边收边渲染

#### Scenario: 拼装上下文生效
- **WHEN** 生成请求携带已注册扩展的 contextId
- **THEN** 发给 LLM 的 system prompt 含该扩展的组件与工具描述

#### Scenario: 空 prompt 被拒绝
- **WHEN** 生成请求的 prompt 为空或仅空白
- **THEN** 返回 400 与错误说明

### Requirement: LLM invocation behavior parity with retired Node server
GenUI Service 的 LLM 调用行为 SHALL 与被替换的 Node 版对齐：通过环境变量 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 配置 OpenAI 兼容接口；支持 `HTTPS_PROXY` 与 `NO_PROXY`（含域名后缀 bypass 判断）；流结束时 `finish_reason` 非 `stop` SHALL 在流尾追加 `\n\n[ERROR: ...]` 文本；流开始前失败 SHALL 返回 502 JSON 错误，流中途失败 SHALL 以错误尾巴结束流。

#### Scenario: 异常收尾追加错误尾巴
- **WHEN** LLM 流以 `finish_reason=length` 或连接中断结束
- **THEN** 响应流末尾追加 `[ERROR: ...]` 说明文本后正常关闭

#### Scenario: 流前失败返回 502
- **WHEN** LLM 接口在产生首个 token 前返回错误
- **THEN** 服务返回 502 与 JSON 错误体，而非空流

#### Scenario: 代理 bypass 生效
- **WHEN** 设置了 `HTTPS_PROXY` 且 `LLM_BASE_URL` 主机命中 `NO_PROXY` 条目
- **THEN** LLM 请求直连，不经过代理

### Requirement: Prompt Override debug bypass
`POST /v1/generate` SHALL 接受可选的 Prompt Override 字段：有值时整段替换 SDK 拼装产物直接作为 system prompt（绕过拼装），仅用于调试实验；Swagger 文档 SHALL 标注 debug-only、生产调用方不应使用。Prompt Override SHALL 不影响任何已注册的 Generation Context，且与 Request Overlay（仅追加 tools/extraRules）语义严格区分。

#### Scenario: Override 绕过拼装
- **WHEN** 生成请求携带非空 Prompt Override
- **THEN** 发给 LLM 的 system prompt 即该 Override 原文，contextId 与 Overlay 字段不参与拼装

#### Scenario: Override 不持久化
- **WHEN** 一次携带 Prompt Override 的生成完成后再次不带 Override 调用
- **THEN** 后一次请求使用正常 SDK 拼装产物，不受前一次影响
