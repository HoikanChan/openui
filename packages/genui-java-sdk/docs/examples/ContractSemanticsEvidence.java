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
                .get("type").equals("T[][]"), "Flatten input");
        for (String name : List.of("First", "Last")) require(entry(entries, name).get("returnType").equals("T | null"), name);
        for (String name : List.of("Sum", "Avg", "Min", "Max")) {
            require(entry(entries, name).get("returnType").equals("number"), name);
        }
        Map<String, Object> sumParam =
                Json.asObject(Json.asList(entry(entries, "Sum").get("params"), "params").getFirst(), "param");
        require(sumParam.get("type").equals("number[]"), "Sum numeric-array input");
        require(entry(entries, "Sum").get("returnType").equals("number"), "Sum number result");
        require(entry(entries, "FormatNumber").get("returnType").equals("any"), "FormatNumber declaration");
        require(entry(entries, "FormatNumber").get("description").toString().contains("returns raw input"), "raw input");
        require(entry(entries, "Each").get("description").toString().contains("Do NOT create a separate statement"),
                "Each stale guidance remains visible");
        // 简洁签名不再声明 input/callForms/binder/nameSyntax:布局由 BuiltinAnalysis 固定。
        for (String name : List.of("Each", "Render")) {
            for (String removed : List.of("input", "callForms", "typeRule", "typeRulesVersion")) {
                require(!entry(entries, name).containsKey(removed), name + " must not declare " + removed);
            }
        }
        require(contract.builtins().stream().filter(b -> b.name().equals("Each")).findFirst().orElseThrow()
                .params().size() == 3, "Each declares three parameters");
        require(contract.builtins().stream().filter(b -> b.name().equals("Render")).findFirst().orElseThrow()
                .params().get(1).optional(), "Render row is optional");
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
