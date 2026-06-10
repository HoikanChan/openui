package com.huawei.cloudsop.genui.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenerationContractLoader {
  private static final String DEFAULT_RESOURCE = "openui/base-contract.json";

  private GenerationContractLoader() {}

  public static GenerationContract loadDefault() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) loader = GenerationContractLoader.class.getClassLoader();
    try (InputStream input = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
      if (input == null) {
        throw new GenerationSdkException("Default base contract resource not found: " + DEFAULT_RESOURCE);
      }
      return fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new GenerationSdkException("Failed to load default base contract", error);
    }
  }

  public static GenerationContract fromJson(String json) {
    return fromMap(Json.asObject(Json.parse(json), "GenerationContract"));
  }

  static GenerationContract fromMap(Map<String, Object> map) {
    return new GenerationContract(
        string(map.get("contractVersion")),
        string(map.get("root")),
        componentMap(map.get("components")),
        componentGroups(map.get("componentGroups")),
        tools(map.get("tools")),
        strings(map.get("examples")),
        strings(map.get("additionalRules")),
        builtins(map.get("builtins")));
  }

  private static List<BuiltinSpec> builtins(Object value) {
    ArrayList<BuiltinSpec> result = new ArrayList<>();
    if (value == null) return result;
    for (Object item : Json.asList(value, "builtins")) {
      Map<String, Object> entry = Json.asObject(item, "builtin");
      result.add(
          new BuiltinSpec(
              string(entry.get("signature")),
              string(entry.get("description")),
              Boolean.TRUE.equals(entry.get("templateBuiltin"))));
    }
    return result;
  }

  private static Map<String, ComponentPromptSpec> componentMap(Object value) {
    LinkedHashMap<String, ComponentPromptSpec> result = new LinkedHashMap<>();
    if (value == null) return result;
    for (Map.Entry<String, Object> entry : Json.asObject(value, "components").entrySet()) {
      Map<String, Object> component = Json.asObject(entry.getValue(), "component " + entry.getKey());
      result.put(
          entry.getKey(),
          new ComponentPromptSpec(string(component.get("signature")), string(component.get("description"))));
    }
    return result;
  }

  private static List<ComponentGroup> componentGroups(Object value) {
    ArrayList<ComponentGroup> result = new ArrayList<>();
    if (value == null) return result;
    for (Object item : Json.asList(value, "componentGroups")) {
      Map<String, Object> group = Json.asObject(item, "componentGroup");
      result.add(
          new ComponentGroup(
              string(group.get("name")), strings(group.get("components")), strings(group.get("notes"))));
    }
    return result;
  }

  private static List<ToolSpec> tools(Object value) {
    ArrayList<ToolSpec> result = new ArrayList<>();
    if (value == null) return result;
    for (Object item : Json.asList(value, "tools")) {
      if (item instanceof String name) {
        result.add(new ToolSpec(name, null, Map.of(), Map.of(), null));
        continue;
      }
      Map<String, Object> tool = Json.asObject(item, "tool");
      result.add(
          new ToolSpec(
              string(tool.get("name")),
              string(tool.get("description")),
              objectOrEmpty(tool.get("inputSchema")),
              objectOrEmpty(tool.get("outputSchema")),
              annotations(tool.get("annotations"))));
    }
    return result;
  }

  private static ToolAnnotations annotations(Object value) {
    if (value == null) return null;
    Map<String, Object> map = Json.asObject(value, "tool annotations");
    return new ToolAnnotations(bool(map.get("readOnlyHint")), bool(map.get("destructiveHint")));
  }

  private static Map<String, Object> objectOrEmpty(Object value) {
    if (value == null) return Map.of();
    return Json.asObject(value, "schema");
  }

  private static List<String> strings(Object value) {
    ArrayList<String> result = new ArrayList<>();
    if (value == null) return result;
    for (Object item : Json.asList(value, "string list")) {
      result.add(string(item));
    }
    return result;
  }

  private static String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Boolean bool(Object value) {
    return value instanceof Boolean bool ? bool : null;
  }
}
