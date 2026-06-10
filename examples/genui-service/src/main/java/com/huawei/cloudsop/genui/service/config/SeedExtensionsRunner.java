package com.huawei.cloudsop.genui.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.cloudsop.genui.core.GenUIContextExtension;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 启动种子:从 classpath:seed/*.json 注册预制扩展(网络运维主题)。
 *
 * <p>注册不持久化——运行期通过 REST 注册的扩展在重启后丢失,只有种子会恢复,
 * 调用方需在自身启动时重注册(PUT 替换语义保证幂等)。
 */
@Component
public class SeedExtensionsRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SeedExtensionsRunner.class);

  private final GenerationAppService appService;
  private final ObjectMapper objectMapper;

  public SeedExtensionsRunner(GenerationAppService appService, ObjectMapper objectMapper) {
    this.appService = appService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath*:seed/*.json");
    Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
    for (Resource resource : resources) {
      try (InputStream in = resource.getInputStream()) {
        GenUIContextExtension extension = objectMapper.readValue(in, GenUIContextExtension.class);
        appService.register(extension);
        log.info("[seed] registered preset context '{}' from {}", extension.contextId(), resource.getFilename());
      }
    }
  }
}
