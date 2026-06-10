package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.cloudsop.genui.core.ComponentGroup;
import com.huawei.cloudsop.genui.core.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.GenUIContextExtension;
import com.huawei.cloudsop.genui.core.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.ToolAnnotations;
import com.huawei.cloudsop.genui.core.ToolSpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
  void restRegisteredExtensionAssemblyMatchesSdkByteForByte() throws Exception {
    // 防回归:REST 注册走 codegen DTO→Jackson→DtoMapper 链,与种子的 ObjectMapper→record 直达链
    // 不同;inputSchema 属性顺序(period/zone/account/limit)在这条链上必须保真,
    // 否则 prompt 字节对齐被破坏(例如有人把 DTO 的 Map 换成会重排序的实现)。
    String registration =
        "{\"version\":\"ba-v1\","
            + "\"components\":{\"BillCard\":{\"signature\":\"BillCard(title: string)\",\"description\":\"Bill card\"}},"
            + "\"componentGroups\":[{\"name\":\"Billing\",\"components\":[\"BillCard\"],\"notes\":[\"note-1\"]}],"
            + "\"tools\":[{\"name\":\"queryBill\",\"description\":\"query bill\","
            + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"period\":{\"type\":\"string\"},\"zone\":{\"type\":\"string\"},\"account\":{\"type\":\"string\"},\"limit\":{\"type\":\"number\"}},\"required\":[\"period\"]},"
            + "\"outputSchema\":{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"number\"},\"rows\":{\"type\":\"array\"}}},"
            + "\"annotations\":{\"readOnlyHint\":true}}],"
            + "\"examples\":[\"root = Stack([])\"],"
            + "\"additionalRules\":[\"rule-1\"]}";
    mvc.perform(
            put("/v1/contexts/byte-align-ext")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isOk());

    String viaRest =
        om.readTree(
                mvc.perform(
                        post("/v1/prompts/assemble")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contextId\":\"byte-align-ext\"}"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8))
            .get("prompt")
            .asText();

    GenerationSdk sdk = GenerationSdk.create();
    sdk.register(sameExtensionViaSdk());
    String viaSdk =
        sdk.assemblePrompt(
                new GenUIPromptRequest("byte-align-ext", null, List.of(), List.of(), null, null, null, null))
            .prompt();

    assertEquals(viaSdk, viaRest, "REST-DTO 路径拼装产物必须与 SDK 直注册逐字节一致");
  }

  private static GenUIContextExtension sameExtensionViaSdk() {
    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.put("BillCard", new ComponentPromptSpec("BillCard(title: string)", "Bill card"));

    LinkedHashMap<String, Object> inputProps = new LinkedHashMap<>();
    inputProps.put("period", Map.of("type", "string"));
    inputProps.put("zone", Map.of("type", "string"));
    inputProps.put("account", Map.of("type", "string"));
    inputProps.put("limit", Map.of("type", "number"));
    LinkedHashMap<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("properties", inputProps);
    inputSchema.put("required", List.of("period"));

    LinkedHashMap<String, Object> outputProps = new LinkedHashMap<>();
    outputProps.put("total", Map.of("type", "number"));
    outputProps.put("rows", Map.of("type", "array"));
    LinkedHashMap<String, Object> outputSchema = new LinkedHashMap<>();
    outputSchema.put("type", "object");
    outputSchema.put("properties", outputProps);

    return new GenUIContextExtension(
        "byte-align-ext",
        "ba-v1",
        components,
        List.of(new ComponentGroup("Billing", List.of("BillCard"), List.of("note-1"))),
        List.of(
            new ToolSpec(
                "queryBill", "query bill", inputSchema, outputSchema, new ToolAnnotations(true, null))),
        List.of("root = Stack([])"),
        List.of("rule-1"));
  }

  @Test
  void groupReferencingMissingComponentReturns400EvenIfNameContainsCollision() throws Exception {
    // 评审复现场景:组件名含 "collision" 子串时,组缺失错误(400)曾被子串匹配误判为 409
    String invalid =
        "{\"version\":\"v1\",\"componentGroups\":[{\"name\":\"G\",\"components\":[\"XcollisionY\"]}]}";
    mvc.perform(put("/v1/contexts/probe-ext").contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest());
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
