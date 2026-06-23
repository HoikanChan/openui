package com.huawei.cloudsop.genui.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.contract.ToolAnnotations;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;

/** 注册端点行为:种子可见、替换语义、名称碰撞 409、未知 extensionId 404。 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class GenerationsApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void listsSeededGenerations() throws Exception {
    JsonNode generations = list();
    Map<String, JsonNode> byId = byextensionId(generations);
    assertTrue(byId.containsKey("noe-alarm-tools"), "seed noe-alarm-tools missing");
    assertTrue(byId.containsKey("noe-ops-rules"), "seed noe-ops-rules missing");
    assertEquals(2, byId.get("noe-alarm-tools").get("toolCount").asInt());
    assertEquals(0, byId.get("noe-alarm-tools").get("componentCount").asInt());
  }

  @Test
  void registersAndReplacesExtension() throws Exception {
    String v1 =
        "{\"version\":\"v1\",\"components\":{\"BizCard\":{\"signature\":\"BizCard(title: string)\",\"description\":\"Business card\"}}}";
    mvc.perform(put("/v1/generations/test-ext").contentType(MediaType.APPLICATION_JSON).content(v1))
        .andExpect(status().isOk());

    String v2 =
        "{\"version\":\"v2\",\"components\":{\"BizCard\":{\"signature\":\"BizCard(title: string)\",\"description\":\"Business card v2\"}}}";
    mvc.perform(put("/v1/generations/test-ext").contentType(MediaType.APPLICATION_JSON).content(v2))
        .andExpect(status().isOk());

    Map<String, JsonNode> byId = byextensionId(list());
    assertEquals("v2", byId.get("test-ext").get("version").asText(), "replace semantics");
  }

  @Test
  void rejectsBaseComponentCollisionWith409() throws Exception {
    String colliding =
        "{\"version\":\"v1\",\"components\":{\"Stack\":{\"signature\":\"Stack()\",\"description\":\"colliding\"}}}";
    mvc.perform(put("/v1/generations/collide-ext").contentType(MediaType.APPLICATION_JSON).content(colliding))
        .andExpect(status().isConflict());

    assertTrue(!byextensionId(list()).containsKey("collide-ext"), "colliding generation must not register");
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
            put("/v1/generations/byte-align-ext")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isOk());

    String viaRest =
        om.readTree(
                mvc.perform(
                        post("/v1/prompts/assemble")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensionId\":\"byte-align-ext\"}"))
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

  private static GenUIExtension sameExtensionViaSdk() {
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

    return new GenUIExtension(
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
  void restRegisteredPropsSchemaComponentAssemblyMatchesSdk() throws Exception {
    // propsSchema 形导出产物经 REST(codegen DTO→Jackson→DtoMapper)注册,拼装产物须与
    // SDK 用规范构造器 (description, propsSchema) 直注册逐字节一致——证明 propsSchema 通路打通,
    // 而非被当作未知字段静默丢弃(那样会渲染成无参的 AlarmBadge())。
    String registration =
        "{\"version\":\"ps-v1\","
            + "\"components\":{\"AlarmBadge\":{\"description\":\"alarm badge\","
            + "\"propsSchema\":{\"type\":\"object\",\"properties\":{\"severity\":{\"type\":\"string\"},\"count\":{\"type\":\"number\"},\"label\":{\"type\":\"string\"}},\"required\":[\"severity\",\"count\"]}}}}";
    mvc.perform(
            put("/v1/generations/props-ext")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
        .andExpect(status().isOk());

    String viaRest =
        om.readTree(
                mvc.perform(
                        post("/v1/prompts/assemble")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"extensionId\":\"props-ext\"}"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8))
            .get("prompt")
            .asText();

    GenerationSdk sdk = GenerationSdk.create();
    sdk.register(propsSchemaExtensionViaSdk());
    String viaSdk =
        sdk.assemblePrompt(
                new GenUIPromptRequest("props-ext", null, List.of(), List.of(), null, null, null, null))
            .prompt();

    assertEquals(viaSdk, viaRest, "propsSchema 形 REST 注册拼装产物须与 SDK 直注册逐字节一致");
  }

  private static GenUIExtension propsSchemaExtensionViaSdk() {
    LinkedHashMap<String, Object> props = new LinkedHashMap<>();
    props.put("severity", Map.of("type", "string"));
    props.put("count", Map.of("type", "number"));
    props.put("label", Map.of("type", "string"));
    LinkedHashMap<String, Object> propsSchema = new LinkedHashMap<>();
    propsSchema.put("type", "object");
    propsSchema.put("properties", props);
    propsSchema.put("required", List.of("severity", "count"));

    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.put("AlarmBadge", new ComponentPromptSpec("alarm badge", propsSchema));

    return new GenUIExtension(
        "props-ext", "ps-v1", components, List.of(), List.of(), List.of(), List.of());
  }

  @Test
  void groupReferencingMissingComponentReturns400EvenIfNameContainsCollision() throws Exception {
    // 评审复现场景:组件名含 "collision" 子串时,组缺失错误(400)曾被子串匹配误判为 409
    String invalid =
        "{\"version\":\"v1\",\"componentGroups\":[{\"name\":\"G\",\"components\":[\"XcollisionY\"]}]}";
    mvc.perform(put("/v1/generations/probe-ext").contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownextensionIdOnAssembleReturns404() throws Exception {
    mvc.perform(
            post("/v1/prompts/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"extensionId\":\"no-such-generation\"}"))
        .andExpect(status().isNotFound());
  }

  private JsonNode list() throws Exception {
    String body =
        mvc.perform(get("/v1/generations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return om.readTree(body);
  }

  private static Map<String, JsonNode> byextensionId(JsonNode generations) {
    Map<String, JsonNode> byId = new HashMap<>();
    generations.forEach(node -> byId.put(node.get("extensionId").asText(), node));
    return byId;
  }
}
