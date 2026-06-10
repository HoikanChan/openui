package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.cloudsop.genui.core.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.GenerationSdk;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 拼装端点行为:与 SDK 直接拼装字节一致、扩展上下文生效、Overlay 工具碰撞 409。 */
@SpringBootTest
@AutoConfigureMockMvc
class PromptsApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void baseAssemblyMatchesSdkByteForByte() throws Exception {
    JsonNode result = assemble("{}");
    String expected =
        GenerationSdk.create()
            .assemblePrompt(
                new GenUIPromptRequest(null, null, List.of(), List.of(), null, null, null, null))
            .prompt();
    assertEquals(expected, result.get("prompt").asText());
    assertEquals(
        "@openuidev/react-ui-dsl@0.1.0", result.get("metadata").get("baseContractVersion").asText());
  }

  @Test
  void extensionContextContributesToolsToPrompt() throws Exception {
    JsonNode result = assemble("{\"contextId\":\"noe-alarm-tools\"}");
    String prompt = result.get("prompt").asText();
    assertTrue(prompt.contains("queryAlarms"), "extension tool must appear in prompt");
    assertEquals("1.0.0", result.get("metadata").get("extensionVersion").asText());
  }

  @Test
  void overlayToolNameCollisionReturns409() throws Exception {
    String body =
        "{\"contextId\":\"noe-alarm-tools\",\"tools\":[{\"name\":\"queryAlarms\",\"description\":\"dup\"}]}";
    mvc.perform(post("/v1/prompts/assemble").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  private JsonNode assemble(String body) throws Exception {
    String response =
        mvc.perform(post("/v1/prompts/assemble").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            // MockHttpServletResponse 默认 ISO-8859-1,必须显式 UTF-8 才能逐字节比对
            .getContentAsString(StandardCharsets.UTF_8);
    return om.readTree(response);
  }
}
