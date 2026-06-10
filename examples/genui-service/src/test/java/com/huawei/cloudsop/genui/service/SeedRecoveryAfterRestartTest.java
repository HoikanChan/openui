package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 重启语义:ContextsApiTest 标记 @DirtiesContext(AFTER_CLASS),本类拿到的是全新应用上下文
 * (等价服务重启)——运行期注册的 test-ext 不复存在,只有种子恢复。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeedRecoveryAfterRestartTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void freshContextContainsExactlySeeds() throws Exception {
    String body =
        mvc.perform(get("/v1/contexts"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode contexts = om.readTree(body);
    Set<String> ids = new TreeSet<>();
    contexts.forEach(node -> ids.add(node.get("contextId").asText()));
    assertEquals(Set.of("noe-alarm-tools", "noe-biz-components", "noe-ops-rules"), ids);
  }
}
