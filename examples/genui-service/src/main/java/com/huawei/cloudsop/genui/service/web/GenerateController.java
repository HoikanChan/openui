package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.DataModelSpec;
import com.huawei.cloudsop.genui.core.GenUIPromptRequest;
import com.huawei.cloudsop.genui.service.api.model.GenerateRequest;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.llm.LlmClient;
import com.huawei.cloudsop.genui.service.llm.LlmStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 手写流式端点:codegen 生成的接口签名(ResponseEntity&lt;String&gt;)无法表达 chunked 流式,
 * 故 /generate 绕过生成接口,但路径与契约保持一致(swagger/genui-service.yaml)。
 *
 * <p>错误行为对齐被替换的 Node 版:流开始前失败 → 502 JSON(异常处理器);
 * 流中途失败或 finish_reason 非 stop → 流尾追加 "[ERROR: ...]" 后正常关闭。
 */
@RestController
public class GenerateController {
  private static final Logger log = LoggerFactory.getLogger(GenerateController.class);
  private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

  private final GenerationAppService appService;
  private final LlmClient llmClient;

  public GenerateController(GenerationAppService appService, LlmClient llmClient) {
    this.appService = appService;
    this.llmClient = llmClient;
  }

  @PostMapping(value = "/v1/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StreamingResponseBody> generate(@RequestBody GenerateRequest body) {
    if (body.getPrompt() == null || body.getPrompt().trim().isEmpty()) {
      throw new IllegalArgumentException("prompt is required");
    }

    // 拼装(404/409 在此抛出)与 LLM 连接(502 在此抛出)都发生在流式响应开始之前
    String systemPrompt = resolveSystemPrompt(body);
    LlmStream stream = llmClient.openStream(systemPrompt, body.getPrompt());

    StreamingResponseBody responseBody =
        out -> {
          String finishReason = null;
          boolean piped = false;
          try {
            finishReason = stream.pipeTo(out);
            piped = true;
          } catch (Exception e) {
            log.error("[generate] stream failed mid-flight: {}", e.getMessage());
            writeTail(out, "[ERROR: " + e.getMessage() + "]");
          } finally {
            closeQuietly(stream);
          }
          if (piped && !"stop".equals(finishReason)) {
            log.warn("[generate] stream ended abnormally, finish_reason = {}", finishReason);
            writeTail(
                out,
                "[ERROR: incomplete, finish_reason="
                    + (finishReason == null ? "connection dropped" : finishReason)
                    + "]");
          }
        };
    return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(responseBody);
  }

  private String resolveSystemPrompt(GenerateRequest body) {
    // Prompt Override(debug-only):整段替换拼装产物,绕过 Generation Context
    if (body.getPromptOverride() != null && !body.getPromptOverride().trim().isEmpty()) {
      return body.getPromptOverride();
    }
    DataModelSpec dataModel =
        body.getDataModel() == null || body.getDataModel().isEmpty()
            ? null
            : new DataModelSpec(null, body.getDataModel());
    GenUIPromptRequest request =
        new GenUIPromptRequest(
            body.getContextId(),
            dataModel,
            DtoMapper.toTools(body.getTools()),
            body.getExtraRules(),
            null,
            null,
            null,
            null);
    return appService.assemble(request).prompt();
  }

  private static void writeTail(OutputStream out, String message) {
    try {
      out.write(("\n\n" + message).getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException ignored) {
      // 客户端已断开,错误尾巴无处可写
    }
  }

  private static void closeQuietly(LlmStream stream) {
    try {
      stream.close();
    } catch (IOException ignored) {
      // 关闭失败不影响响应
    }
  }
}
