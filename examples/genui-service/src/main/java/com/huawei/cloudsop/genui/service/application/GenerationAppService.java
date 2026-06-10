package com.huawei.cloudsop.genui.service.application;

import com.huawei.cloudsop.genui.core.GenUIContextExtension;
import com.huawei.cloudsop.genui.core.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.GenerationSdk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 用例编排层:DTO 之下、SDK 之上。
 *
 * <p>SDK 不暴露已注册扩展的枚举能力(且本服务不修改 packages/ 库代码),所以这里维护一份只读镜像,
 * 仅在 SDK 注册成功后写入,用于 contexts 列表与未知 contextId 的 404 判断。
 */
@Service
public class GenerationAppService {
  private final GenerationSdk sdk;
  private final Map<String, GenUIContextExtension> registered = new LinkedHashMap<>();

  public GenerationAppService(GenerationSdk sdk) {
    this.sdk = sdk;
  }

  public synchronized ContextSummaryData register(GenUIContextExtension extension) {
    sdk.register(extension);
    registered.put(extension.contextId(), extension);
    return summarize(extension);
  }

  public synchronized List<ContextSummaryData> listContexts() {
    List<ContextSummaryData> summaries = new ArrayList<>();
    for (GenUIContextExtension extension : registered.values()) {
      summaries.add(summarize(extension));
    }
    return summaries;
  }

  public synchronized GenUIPromptAssemblyResult assemble(GenUIPromptRequest request) {
    // SDK 对未注册的 contextId 会静默回落到 base contract;服务层选择 fail loudly。
    if (request.contextId() != null && !registered.containsKey(request.contextId())) {
      throw new UnknownContextException(request.contextId());
    }
    return sdk.assemblePrompt(request);
  }

  private static ContextSummaryData summarize(GenUIContextExtension extension) {
    return new ContextSummaryData(
        extension.contextId(),
        extension.version(),
        extension.components().size(),
        extension.tools().size());
  }
}
