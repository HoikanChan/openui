package com.huawei.cloudsop.genui.service.config;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SdkConfig {
  /** 默认 base contract 即 DSLEngine 组件契约,由 SDK 内置资源提供。 */
  @Bean
  public GenerationSdk generationSdk() {
    return GenerationSdk.create();
  }
}
