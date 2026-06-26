# SDK LLM Invocation Migration Notes

This note is for the internal service repository that currently owns LLM request assembly, SSE parsing, response parsing, caching, and OpenUI code extraction.

## Delegate slimming

`GenUIServiceDelegateImpl` should keep service-layer responsibilities only:

1. Compute the existing cache key with the current sha256 inputs.
2. Read Redis/cache and return cached OpenUI code on hit.
3. Map the request into `UiGenerationRequest`.
4. Call `GenUiGenerator.generate(...)` for sync generation or `generateStream(..., sink)` for streaming generation.
5. Store the final extracted OpenUI code in cache.
6. For `text/event-stream`, wrap each callback delta with `SseFrames.of(delta)` and emit `SseFrames.done()` at the end if the endpoint contract needs an explicit done frame.

The delegate should delete its local chat/completions request body construction, response DTO parsing, SSE frame parsing, and OpenUI code extraction after the SDK is adopted.

The SDK defaults `jsonObjectResponse` to `false` so the model can return
openui-lang text. If a legacy gateway still requires `response_format` with
`{"type":"json_object"}`, configure `GenUiLlmConfig.builder().jsonObjectResponse(true)`.

## Internal classes to delete after migration

The following internal classes are replaced by SDK classes:

| Internal class | SDK replacement |
| --- | --- |
| `LLMService` | `GenUiGenerator` + default `RestfulLlmTransport` |
| `ChatCompletionsRsp` | `ChatCompletionResponse` |
| Delegate SSE chunk parser | `SseDeltaParser` |
| Delegate SSE frame writer | `SseFrames` |
| Delegate `extractOpenuiCode` / markdown extraction | `OpenuiCodeExtractor` |

Keep Redis/Jedis, Spring wiring, request validation, cache key calculation, and service-specific fail-loud checks in the service layer.

## UIRequestDetail to UiGenerationRequest mapping

| `UIRequestDetail` / old prompt field | `UiGenerationRequest` target | Notes |
| --- | --- | --- |
| `extensionId` | `extensionId` | Selects the registered SDK extension. Service layer may still fail loud if the extension is not registered. |
| `userInput` | `userInput` | Sent as the user message. SDK appends ` /no_think`. |
| `apiRsp` | `response` | Main data source for `DataModelSpec`; keep service-layer preprocessing or sampling before passing to SDK. |
| `apiReq` | `request` | Auxiliary request context; SDK includes it in the user message as JSON context. |
| `apiUrl` | `request.apiUrl` | Preserve only if useful to generation quality. |
| `apiVersion` | `request.apiVersion` | Preserve only if useful to generation quality. |
| `suggestion` / extra prompt text | `suggestion` | Appended to SDK `extraRules`. |
| edit/inline/tool-calls/bindings flags | `editMode` / `inlineMode` / `toolCalls` / `bindings` | Passed through to `assemblePrompt`. |
| `templateId`, `renderPiu`, `iframeUrl`, `iframeTitle`, `scenario`, `source` | service layer only | Do not pass to SDK unless explicitly needed as request context. |

## Points to verify against `PromptTemplateUtil`

- Whether old `{apiRsp}` content had additional explanatory text that should become `suggestion` or registered extension `additionalRules`.
- Whether old `{userInput}` placement materially differs from the SDK system+user message split.
- Whether `apiReq` / `apiUrl` / `apiVersion` should always be included in request context or only for specific scenarios.
- Whether streaming endpoints should emit raw text chunks or SSE frames; SDK supports both by combining callback deltas with optional `SseFrames` wrapping.

## BSP dependency note

Local/offline builds can install the stub under `bsp-stub/`. BSP runtime builds should use the real `com.huawei.bsp:com.huawei.bsp.commonlib.resetclient:25.590.54` dependency and remove the local install step once the artifact is available in the build environment.
