package com.huawei.cloudsop.genui.service.application;

import com.huawei.cloudsop.genui.core.GenUIGeneration;
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
 * 仅在 SDK 注册成功后写入,用于 generations 列表与未知 extensionId 的 404 判断。
 */
@Service
public class GenerationAppService {
  private final GenerationSdk sdk;
  private final Map<String, GenUIGeneration> registered = new LinkedHashMap<>();

  public GenerationAppService(GenerationSdk sdk) {
    this.sdk = sdk;
  }

  public synchronized GenerationSummaryData register(GenUIGeneration generation) {
    sdk.register(generation);
    registered.put(generation.extensionId(), generation);
    return summarize(generation);
  }

  public synchronized List<GenerationSummaryData> listGenerations() {
    List<GenerationSummaryData> summaries = new ArrayList<>();
    for (GenUIGeneration generation : registered.values()) {
      summaries.add(summarize(generation));
    }
    return summaries;
  }

  public synchronized GenUIPromptAssemblyResult assemble(GenUIPromptRequest request) {
    // SDK 对未注册的 extensionId 会静默回落到 base contract;服务层选择 fail loudly。
    if (request.extensionId() != null && !registered.containsKey(request.extensionId())) {
      throw new UnknownGenerationException(request.extensionId());
    }
    return sdk.assemblePrompt(request);
  }

  private static GenerationSummaryData summarize(GenUIGeneration generation) {
    return new GenerationSummaryData(
        generation.extensionId(),
        generation.version(),
        generation.components().size(),
        generation.tools().size());
  }
}
