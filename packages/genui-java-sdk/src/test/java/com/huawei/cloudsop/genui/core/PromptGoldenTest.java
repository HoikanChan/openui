package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Cross-language byte-for-byte alignment test (design D9).
 *
 * <p>Each {@code prompt-golden/<name>.json} is a merged {@code PromptSpec}; the matching
 * {@code <name>.txt} is produced by the TypeScript {@code generatePrompt} oracle (regenerate via
 * {@code pnpm --dir packages/lang-core run generate:prompt-golden}). This test feeds the same fixture
 * into {@link PromptAssembler#assemble} and asserts the output equals the golden exactly.
 */
class PromptGoldenTest {

  /** The builtins manifest is global/invariant, so source it from the bundled base contract. */
  private static final List<BuiltinSpec> BUILTINS = GenerationContractLoader.loadDefault().builtins();

  @TestFactory
  Stream<DynamicTest> assemblerMatchesTypeScriptGolden() throws Exception {
    Path goldenDir = goldenDir();
    List<Path> fixtures;
    try (Stream<Path> files = Files.list(goldenDir)) {
      fixtures =
          files
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .toList();
    }

    return fixtures.stream()
        .map(
            fixture -> {
              String name = fixture.getFileName().toString().replaceFirst("\\.json$", "");
              return DynamicTest.dynamicTest(
                  name,
                  () -> {
                    String specJson = Files.readString(fixture, StandardCharsets.UTF_8);
                    PromptAssembler.PromptInput input =
                        toInput(Json.asObject(Json.parse(specJson), "fixture " + name));
                    String expected =
                        Files.readString(goldenDir.resolve(name + ".txt"), StandardCharsets.UTF_8);
                    String actual = PromptAssembler.assemble(input, BUILTINS);
                    assertEquals(expected, actual, "Prompt drift for fixture " + name);
                  });
            });
  }

  private static Path goldenDir() throws Exception {
    URL url = Thread.currentThread().getContextClassLoader().getResource("prompt-golden");
    if (url == null) throw new IllegalStateException("prompt-golden not found on test classpath");
    return Path.of(url.toURI());
  }

  // ─── fixture (PromptSpec JSON) → PromptInput, mirroring generatePrompt(spec) ───

  private static PromptAssembler.PromptInput toInput(Map<String, Object> spec) {
    // generatePrompt concatenates examples + toolExamples.
    List<String> examples = new ArrayList<>(strings(spec.get("examples")));
    examples.addAll(strings(spec.get("toolExamples")));

    return new PromptAssembler.PromptInput(
        string(spec.get("preamble")),
        string(spec.get("root")),
        components(spec.get("components")),
        componentGroups(spec.get("componentGroups")),
        dataModel(spec.get("dataModel")),
        tools(spec.get("tools")),
        examples,
        strings(spec.get("additionalRules")),
        bool(spec.get("editMode")),
        bool(spec.get("inlineMode")),
        bool(spec.get("toolCalls")),
        bool(spec.get("bindings")));
  }

  private static Map<String, ComponentPromptSpec> components(Object value) {
    LinkedHashMap<String, ComponentPromptSpec> result = new LinkedHashMap<>();
    if (value == null) return result;
    for (Map.Entry<String, Object> entry : Json.asObject(value, "components").entrySet()) {
      Map<String, Object> comp = Json.asObject(entry.getValue(), "component");
      result.put(
          entry.getKey(),
          new ComponentPromptSpec(string(comp.get("signature")), string(comp.get("description"))));
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
      Map<String, Object> tool = Json.asObject(item, "tool");
      result.add(
          new ToolSpec(
              string(tool.get("name")),
              string(tool.get("description")),
              schema(tool.get("inputSchema")),
              schema(tool.get("outputSchema")),
              null));
    }
    return result;
  }

  private static DataModelSpec dataModel(Object value) {
    if (value == null) return null;
    Map<String, Object> map = Json.asObject(value, "dataModel");
    Object raw = map.get("raw");
    return new DataModelSpec(
        string(map.get("description")), raw == null ? null : Json.asObject(raw, "dataModel.raw"));
  }

  private static Map<String, Object> schema(Object value) {
    return value == null ? null : Json.asObject(value, "schema");
  }

  private static List<String> strings(Object value) {
    ArrayList<String> result = new ArrayList<>();
    if (value == null) return result;
    for (Object item : Json.asList(value, "string list")) result.add(string(item));
    return result;
  }

  private static String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Boolean bool(Object value) {
    return value instanceof Boolean flag ? flag : null;
  }
}
