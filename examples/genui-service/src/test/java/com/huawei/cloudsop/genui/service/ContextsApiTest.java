package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;

/** 注册端点行为:种子可见、替换语义、名称碰撞 409、未知 contextId 404。 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ContextsApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void listsSeededContexts() throws Exception {
    JsonNode contexts = list();
    Map<String, JsonNode> byId = byContextId(contexts);
    assertTrue(byId.containsKey("noe-alarm-tools"), "seed noe-alarm-tools missing");
    assertTrue(byId.containsKey("noe-ops-rules"), "seed noe-ops-rules missing");
    assertEquals(2, byId.get("noe-alarm-tools").get("toolCount").asInt());
    assertEquals(0, byId.get("noe-alarm-tools").get("componentCount").asInt());
  }

  @Test
  void registersAndReplacesExtension() throws Exception {
    String v1 =
        "{\"version\":\"v1\",\"components\":{\"BizCard\":{\"signature\":\"BizCard(title: string)\",\"description\":\"Business card\"}}}";
    mvc.perform(put("/v1/contexts/test-ext").contentType(MediaType.APPLICATION_JSON).content(v1))
        .andExpect(status().isOk());

    String v2 =
        "{\"version\":\"v2\",\"components\":{\"BizCard\":{\"signature\":\"BizCard(title: string)\",\"description\":\"Business card v2\"}}}";
    mvc.perform(put("/v1/contexts/test-ext").contentType(MediaType.APPLICATION_JSON).content(v2))
        .andExpect(status().isOk());

    Map<String, JsonNode> byId = byContextId(list());
    assertEquals("v2", byId.get("test-ext").get("version").asText(), "replace semantics");
  }

  @Test
  void rejectsBaseComponentCollisionWith409() throws Exception {
    String colliding =
        "{\"version\":\"v1\",\"components\":{\"Stack\":{\"signature\":\"Stack()\",\"description\":\"colliding\"}}}";
    mvc.perform(put("/v1/contexts/collide-ext").contentType(MediaType.APPLICATION_JSON).content(colliding))
        .andExpect(status().isConflict());

    assertTrue(!byContextId(list()).containsKey("collide-ext"), "colliding context must not register");
  }

  @Test
  void unknownContextIdOnAssembleReturns404() throws Exception {
    mvc.perform(
            post("/v1/prompts/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contextId\":\"no-such-context\"}"))
        .andExpect(status().isNotFound());
  }

  private JsonNode list() throws Exception {
    String body =
        mvc.perform(get("/v1/contexts"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return om.readTree(body);
  }

  private static Map<String, JsonNode> byContextId(JsonNode contexts) {
    Map<String, JsonNode> byId = new HashMap<>();
    contexts.forEach(node -> byId.put(node.get("contextId").asText(), node));
    return byId;
  }
}
