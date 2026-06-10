package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.service.api.model.ErrorResponse;
import com.huawei.cloudsop.genui.service.application.UnknownContextException;
import com.huawei.cloudsop.genui.service.llm.LlmUpstreamException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * SDK 只抛一种异常类型;名称碰撞类消息固定含 "collision"(见 GenerationSdk),
   * 据此映射 409,其余视为非法输入 400。
   */
  @ExceptionHandler(GenerationSdkException.class)
  public ResponseEntity<ErrorResponse> handleSdk(GenerationSdkException ex) {
    String message = ex.getMessage() == null ? "invalid request" : ex.getMessage();
    HttpStatus status =
        message.toLowerCase(Locale.ROOT).contains("collision")
            ? HttpStatus.CONFLICT
            : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(new ErrorResponse().error(message));
  }

  @ExceptionHandler(UnknownContextException.class)
  public ResponseEntity<ErrorResponse> handleUnknownContext(UnknownContextException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse().error(ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(new ErrorResponse().error(ex.getMessage()));
  }

  /** LLM 在产生首个 token 前失败 → 502;流中途失败由 GenerateController 以错误尾巴处理。 */
  @ExceptionHandler(LlmUpstreamException.class)
  public ResponseEntity<ErrorResponse> handleLlmUpstream(LlmUpstreamException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse().error(ex.getMessage()));
  }
}
