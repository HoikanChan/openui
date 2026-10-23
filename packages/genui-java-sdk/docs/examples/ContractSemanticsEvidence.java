import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Contract evidence only: no DSL execution and no runtime result claims. */
public class ContractSemanticsEvidence {
    public static void main(String[] args) throws Exception {
        Path sdk = Path.of(args.length == 0 ? "packages/genui-java-sdk" : args[0]);
        byte[] bytes = Files.readAllBytes(sdk.resolve("src/main/resources/openui/base-contract.json"));
        var contract = GenerationContractLoader.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        List<String> names = List.of("Count", "First", "Last", "Sum", "Avg", "Min", "Max", "Sort", "Filter",
                "Flatten", "ObjectEntries", "ObjectKeys", "ObjectValues", "Round", "Abs", "Floor", "Ceil", "Number",
                "Switch", "FormatDate", "FormatBytes", "FormatNumber", "FormatPercent", "FormatDuration",
                "MergeObject", "Join", "String", "Each", "Render");
        require(contract.builtins().stream().map(b -> b.name()).toList().equals(names), "29 builtin names/order");
        for (var builtin : contract.builtins()) {
            System.out.println(Json.stringify(Map.of("name", builtin.name(), "minArity", builtin.minArity(),
                    "maxArity", builtin.maxArity().map(Object::toString).orElse("unbounded"),
                    "description", builtin.description())));
        }
        var raw = Json.asObject(Json.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)), "contract");
        var entries = Json.asList(raw.get("builtins"), "builtins").stream()
                .map(value -> Json.asObject(value, "builtin")).toList();
        Map<String, Object> merge = entry(entries, "MergeObject");
        require(contract.builtins().stream().filter(b -> b.name().equals("MergeObject")).findFirst().orElseThrow()
                .minArity() == 2, "MergeObject min arity");
        require(merge.get("description").toString().contains("Non-object arguments are skipped"), "Merge tolerance");
        require(merge.get("description").toString().contains("Returns {} when no objects are provided"), "Merge empty");
        require(Json.asObject(Json.asList(entry(entries, "Flatten").get("params"), "params").getFirst(), "param")
                .get("type").equals("array[]"), "Flatten input");
        for (String name : List.of("First", "Last")) require(entry(entries, name).get("returnType").equals("T"), name);
        for (String name : List.of("Sum", "Avg", "Min", "Max")) {
            require(entry(entries, name).get("returnType").equals("number"), name);
        }
        var sumRule = Json.asObject(entry(entries, "Sum").get("typeRule"), "Sum rule");
        var sumParameter = Json.asObject(Json.asList(sumRule.get("parameters"), "parameters").getFirst(), "parameter");
        require(Json.asObject(sumParameter.get("accept"), "Sum input").equals(
                Map.of("tag", "sequence", "element", "number")), "Sum executable numeric-array input");
        require(Json.asObject(sumRule.get("result"), "Sum result").equals(Map.of("type", "number")),
                "Sum executable number result");
        require(entry(entries, "FormatNumber").get("returnType").equals("string"), "FormatNumber declaration");
        require(entry(entries, "FormatNumber").get("description").toString().contains("returns raw input"), "raw input");
        require(entry(entries, "Each").get("description").toString().contains("Do NOT create a separate statement"),
                "Each stale guidance remains visible");
        require(((Number) entry(entries, "Each").get("input")).intValue() == 0, "Each element source index");
        require(entry(entries, "Render").containsKey("input") && entry(entries, "Render").get("input") == null,
                "Render explicitly declares no element source");
        require(contract.builtins().stream().filter(b -> b.name().equals("Each")).findFirst().orElseThrow()
                .callForms().equals(List.of(new com.huawei.cloudsop.genui.core.contract.BuiltinSpec.CallForm(3, 2,
                        List.of(1)))), "Each compiled call form");
        require(contract.builtins().stream().filter(b -> b.name().equals("Render")).findFirst().orElseThrow()
                .inputIndex() == -1, "Render has no element source");
        var acceptance = Json.asObject(Json.parse(Files.readString(sdk.resolve("docs/examples/contract-migration-acceptance.json"))), "acceptance");
        require(acceptance.get("contractVersion").equals(contract.contractVersion()), "acceptance version");
        var cases = Json.asList(acceptance.get("cases"), "cases");
        var ids = new HashSet<String>();
        int unproven = 0;
        for (var value : cases) {
            var sample = Json.asObject(value, "sample");
            require(ids.add(sample.get("id").toString()), "unique id");
            require(sample.containsKey("source"), "case source");
            if (sample.getOrDefault("preciseResult", "").toString().startsWith("unresolved")) {
                require(!sample.containsKey("expectedValue"), "no guessed expected value");
                require("UNPROVEN".equals(sample.get("precisionStatus")), "explicit precision gap");
                unproven++;
            }
        }
        System.out.println(Json.stringify(Map.of("acceptanceCases", cases.size(), "unprovenPrecisionGaps", unproven)));
        System.out.println(Json.stringify(Map.of("contractVersion", contract.contractVersion(), "builtins", names.size(),
                "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "evidence", "contract-only; no runtime execution")));
    }

    private static Map<String, Object> entry(List<Map<String, Object>> entries, String name) {
        return entries.stream().filter(value -> name.equals(value.get("name"))).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
