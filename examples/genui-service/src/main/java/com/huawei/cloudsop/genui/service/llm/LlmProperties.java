package com.huawei.cloudsop.genui.service.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

/** 环境变量沿用被替换 Node 版的同名约定(LLM_API_KEY/LLM_BASE_URL/...),见 application.yml。 */
@ConfigurationProperties(prefix = "genui.llm")
@ConstructorBinding
public record LlmProperties(String apiKey, String baseUrl, String model, String httpsProxy, String noProxy) {
  public LlmProperties {
    model = model == null || model.isBlank() ? "deepseek-chat" : model;
  }
}
