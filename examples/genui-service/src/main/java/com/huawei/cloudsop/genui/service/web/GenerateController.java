package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.GateDecision;
import com.huawei.cloudsop.genui.core.validation.stream.StreamingValidationSession;
import com.huawei.cloudsop.genui.service.api.model.GenerateRequest;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.llm.LlmClient;
import com.huawei.cloudsop.genui.service.llm.LlmStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
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
 * <p>响应为 Server-Sent Events(text/event-stream),每帧一行 {@code data: <json>\n\n},{@code <json>}
 * 是序列化后的 SDK {@link RenderStreamEnvelope}(type/seq/content)。协议(design Decision #7):
 * 首帧 {@code dataModel}(seq=0)→ 若干 {@code dsl}(仅 SDK 校验门放行的完整语句)→ 结束 {@code done};
 * 校验门 withhold/fail 时发 {@code error(VALIDATION_FAILED)} 后停止,流中途失败或 finish_reason 非 stop
 * 时发 {@code error(LLM_STREAM_FAILED)} 后 {@code done}。
 *
 * <p>错误行为(流开始前):拼装 404 / 空 prompt 400 / LLM 连接失败 502 都在响应体开始前抛出,由异常
 * 处理器返回 JSON —— 所以任何 SSE 帧都在 openStream 成功之后,502 契约不变。
 */
@RestController
public class GenerateController {
  private static final Logger log = LoggerFactory.getLogger(GenerateController.class);

  private final GenerationAppService appService;
  private final GenerationSdk sdk;
  private final LlmClient llmClient;

  public GenerateController(
      GenerationAppService appService, GenerationSdk sdk, LlmClient llmClient) {
    this.appService = appService;
    this.sdk = sdk;
    this.llmClient = llmClient;
  }

  @PostMapping(value = "/v1/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StreamingResponseBody> generate(@RequestBody GenerateRequest body) {
    if (body.getPrompt() == null || body.getPrompt().trim().isEmpty()) {
      throw new IllegalArgumentException("prompt is required");
    }

    // 拼装(404 在此抛出)与 LLM 连接(502 在此抛出)都发生在流式响应开始之前。
    GenUIPromptRequest request = toPromptRequest(body);
    String systemPrompt = resolveSystemPrompt(body, request);
    // 校验门用的合并契约必须与 LLM 被 prompt 的契约一致(相同组件名/props)。
    GenerationContract merged = sdk.mergedContract(request);
    LlmStream stream = llmClient.openStream(systemPrompt, body.getPrompt());

    StreamingResponseBody responseBody =
        out -> streamEnvelopes(out, stream, merged, body.getDataModel());
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(responseBody);
  }

  /** 驱动 SDK 校验门,把 envelope 序列化为 SSE 帧。异常不外抛(首帧已发出),转为 error 帧。 */
  private void streamEnvelopes(
      OutputStream out,
      LlmStream stream,
      GenerationContract merged,
      java.util.Map<String, Object> dataModel) {
    StreamingValidationSession session =
        new StreamingValidationSession(new DefaultOpenuiLangValidator(), merged, merged.root());
    int[] nextSeq = {1};
    ValidationStatus finalStatus = null;
    try {
      // 首帧:dataModel(seq=0)—— 在 openStream 成功后发出,502 仍先于任何帧。
      writeFrame(out, RenderStreamEnvelope.dataModel(dataModel));

      boolean[] withheld = {false};
      String finishReason =
          stream.pipeToConsumer(
              delta -> {
                if (withheld[0]) {
                  return;
                }
                if (applyDecisions(out, session.onDelta(delta), nextSeq)) {
                  withheld[0] = true;
                }
              });

      if (withheld[0]) {
        // 校验门已 withhold:发 error(VALIDATION_FAILED) 后结束,被 withhold 的语句文本从不进 dsl 帧。
        writeFrame(
            out,
            RenderStreamEnvelope.error(
                nextSeq[0]++, "VALIDATION_FAILED", withheldMessage(session), false));
        writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
        finalStatus = ValidationStatus.INVALID;
        return;
      }

      if (!"stop".equals(finishReason)) {
        // finish_reason 非 stop(截断/连接被掐断):发 error(LLM_STREAM_FAILED) 后 done(取代旧文本尾巴)。
        String msg =
            "incomplete, finish_reason="
                + (finishReason == null ? "connection dropped" : finishReason);
        log.warn("[generate] stream ended abnormally: {}", msg);
        writeFrame(out, RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", msg, true));
        writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
        finalStatus = ValidationStatus.INVALID;
        return;
      }

      // 干净结束:flush 尚在缓冲的语句(onEnd),终态 FAIL → error;否则 done。
      if (applyDecisions(out, session.onEnd(), nextSeq)) {
        writeFrame(
            out,
            RenderStreamEnvelope.error(
                nextSeq[0]++, "VALIDATION_FAILED", withheldMessage(session), false));
        writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
        finalStatus = ValidationStatus.INVALID;
        return;
      }
      writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
      ValidationResult result = session.latestValidationResult();
      finalStatus = result == null ? ValidationStatus.VALID : result.status();
    } catch (Exception e) {
      // 流中途失败:发 error(LLM_STREAM_FAILED) 后 done(取代旧文本尾巴)。
      log.error("[generate] stream failed mid-flight: {}", e.getMessage());
      writeFrame(out, RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", e.getMessage(), true));
      writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
      finalStatus = ValidationStatus.INVALID;
    } finally {
      closeQuietly(stream);
      // 9.2 缓存决策:本服务不缓存生成的 DSL 结果(GenerationsController/AppService 只记录已注册扩展,
      // 无 DSL 结果缓存),故不新造缓存(YAGNI)。此处记录最终 validationStatus,供未来的缓存消费者
      // 据此只缓存 VALID(或修复后 VALID)的结果。
      log.info(
          "[generate] stream complete, validationStatus={} (cache only VALID/REPAIRED)", finalStatus);
    }
  }

  /**
   * 应用一批门决策:EMIT → dsl 帧;BUFFER → 不发帧(门内部持有);WITHHOLD/FAIL → 返回 true 触发停止
   * (不在此发 error 帧,由调用方决定终态)。
   */
  private boolean applyDecisions(OutputStream out, List<GateDecision> decisions, int[] nextSeq) {
    for (GateDecision decision : decisions) {
      switch (decision.kind()) {
        case EMIT -> writeFrame(out, RenderStreamEnvelope.dsl(nextSeq[0]++, decision.statementText()));
        case BUFFER -> {
          // 门内部持有,flush 前不发帧
        }
        case WITHHOLD, FAIL -> {
          return true;
        }
        default -> {
          // no-op
        }
      }
    }
    return false;
  }

  private GenUIPromptRequest toPromptRequest(GenerateRequest body) {
    DataModelSpec dataModel =
        body.getDataModel() == null || body.getDataModel().isEmpty()
            ? null
            : new DataModelSpec(null, body.getDataModel());
    return new GenUIPromptRequest(
        body.getExtensionId(), dataModel, body.getExtraRules(), null, null, null, null);
  }

  private String resolveSystemPrompt(GenerateRequest body, GenUIPromptRequest request) {
    // Prompt Override(debug-only):整段替换拼装产物,绕过 Generation。
    if (body.getPromptOverride() != null && !body.getPromptOverride().trim().isEmpty()) {
      return body.getPromptOverride();
    }
    return appService.assemble(request).prompt();
  }

  /**
   * 序列化 envelope 为一行 SSE 帧 {@code data: <json>\n\n}。SDK 的 {@link Json#stringify} 面向
   * Map/List/标量,不会把 record 序列化成 JSON 对象,故先转成 {@code {type, seq, content}} map 再序列化。
   * {@code done} 帧的 content 为 null,按契约省略 content 字段。
   */
  private static void writeFrame(OutputStream out, RenderStreamEnvelope envelope) {
    try {
      java.util.LinkedHashMap<String, Object> frame = new java.util.LinkedHashMap<>();
      frame.put("type", envelope.type());
      frame.put("seq", envelope.seq());
      if (envelope.content() != null) {
        frame.put("content", envelope.content());
      }
      String json = Json.stringify(frame);
      out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException ignored) {
      // 客户端已断开,帧无处可写
    }
  }

  private static String withheldMessage(StreamingValidationSession session) {
    ValidationResult result = session.withheldResult();
    if (result == null) {
      return "Generated DSL failed streaming validation";
    }
    return "Generated DSL failed streaming validation: " + buildIssueSummary(result);
  }

  /** 汇总前几条 blocking issue 作为 error message(镜像 SDK GenUiGenerator 的写法)。 */
  private static String buildIssueSummary(ValidationResult result) {
    return result.issues().stream()
        .filter(i -> i.severity() == ValidationSeverity.ERROR)
        .limit(3)
        .map(i -> "[" + i.code() + "] " + i.message())
        .collect(Collectors.joining("; "));
  }

  private static void closeQuietly(LlmStream stream) {
    try {
      stream.close();
    } catch (IOException ignored) {
      // 关闭失败不影响响应
    }
  }
}
