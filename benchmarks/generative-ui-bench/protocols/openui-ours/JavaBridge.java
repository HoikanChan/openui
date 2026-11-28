import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Thin stdin/stdout JSON bridge calling our real packages/genui-java-sdk classes:
 * GenerationSdk (PromptAssembler + DataProfiler + insight few-shots) and
 * DefaultOpenuiLangValidator (parser + semantic + TypeAnalysis + ComponentConsumerValidator).
 */
public final class JavaBridge {
    private static final Pattern THINK_BLOCK = Pattern.compile("^[\\s\\S]*?</think>\\s*");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: JavaBridge <assemble-prompts|validate-batch>");
        }
        String stdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        String cmd = args[0];
        GenerationSdk sdk = GenerationSdk.create();
        switch (cmd) {
            case "assemble-prompts" -> {
                List<Object> items = Json.asList(Json.parse(stdin), "items");
                Map<String, Object> out = new LinkedHashMap<>();
                for (Object raw : items) {
                    Map<String, Object> item = Json.asObject(raw, "item");
                    String name = String.valueOf(item.get("name"));
                    String prompt = String.valueOf(item.get("prompt"));
                    Map<String, Object> dm = asOptionalMap(item.get("dataModel"));
                    DataModelSpec dmSpec =
                            (dm == null || dm.isEmpty()) ? null : new DataModelSpec("Response data", dm);
                    GenUIPromptRequest req =
                            new GenUIPromptRequest(null, dmSpec, List.of(), null, null, null, null);
                    GenUIPromptAssemblyResult assembled = sdk.assemblePrompt(req);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("systemPrompt", assembled.prompt());
                    entry.put("userPrompt", prompt);
                    entry.put("contractVersion", assembled.metadata().baseContractVersion());
                    entry.put("hasDataModel", dmSpec != null);
                    out.put(name, entry);
                }
                System.out.print(Json.stringify(out));
            }
            case "validate-batch" -> {
                List<Object> items = Json.asList(Json.parse(stdin), "items");
                GenerationContract contract = sdk.baseContract();
                BuiltinCatalog builtins = BuiltinCatalog.fromContract(contract);
                List<Object> results = new ArrayList<>(items.size());
                for (Object raw : items) {
                    Map<String, Object> item = Json.asObject(raw, "item");
                    results.add(validateOne(item, contract, builtins));
                }
                System.out.print(Json.stringify(results));
            }
            default -> throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    private static Map<String, Object> validateOne(
            Map<String, Object> item, GenerationContract contract, BuiltinCatalog builtins) {
        String id = String.valueOf(item.get("id"));
        String text = item.get("text") == null ? "" : String.valueOf(item.get("text"));
        Map<String, Object> dm = asOptionalMap(item.get("dataModel"));
        boolean truncated = Boolean.TRUE.equals(item.get("truncated"));
        int reqs = item.get("reqs") instanceof Number n ? n.intValue() : 0;

        String stripped = stripThink(text);
        String extracted = OpenuiCodeExtractor.extract(stripped);

        ValidationRequest req =
                ValidationRequest.builder()
                        .dsl(extracted)
                        .contract(contract)
                        .rootName(contract.root())
                        .dataModel(dm)
                        .build();
        ValidationResult result = DefaultOpenuiLangValidator.INSTANCE.validate(req);

        Program program = OpenuiParser.parse(extracted, builtins);
        String entryId = result.metadata() == null ? null : result.metadata().rootName();
        int reachable = countReachableComponents(program, entryId);

        List<Map<String, Object>> errs = new ArrayList<>();
        List<Map<String, Object>> rawIssues = new ArrayList<>();
        boolean rootError = false;
        for (ValidationIssue issue : result.issues()) {
            Map<String, Object> rawIssue = new LinkedHashMap<>();
            rawIssue.put("code", issue.code());
            rawIssue.put("severity", issue.severity().name());
            rawIssue.put("source", issue.source());
            rawIssue.put("message", issue.message());
            rawIssue.put("component", issue.component());
            rawIssue.put("path", issue.path());
            rawIssues.add(rawIssue);

            if (issue.severity() != ValidationSeverity.ERROR) {
                continue;
            }
            if ("root-missing".equals(issue.code()) || "root-not-renderable".equals(issue.code())) {
                rootError = true;
            }
            String cls = mapErrorClass(issue);
            String detail = buildDetail(issue);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("cls", cls);
            err.put("detail", detail);
            errs.add(err);
        }

        boolean renderable = entryId != null && !rootError && reachable > 0;
        if (truncated) {
            errs.add(Map.of("cls", "truncation", "detail", "hit ceiling"));
        }
        boolean complete = errs.isEmpty();
        if (complete && reachable < reqs) {
            errs.add(
                    Map.of(
                            "cls",
                            "coverage-floor",
                            "detail",
                            "components " + reachable + " < requirements " + reqs));
            complete = false;
        }

        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("id", id);
        verdict.put("renderable", renderable);
        verdict.put("complete", complete);
        verdict.put("n", reachable);
        verdict.put("errs", errs);
        verdict.put("rawIssues", rawIssues);
        return verdict;
    }

    private static String mapErrorClass(ValidationIssue issue) {
        String code = issue.code() == null ? "" : issue.code();
        String source = issue.source() == null ? "" : issue.source();
        String msg = issue.message() == null ? "" : issue.message();
        if ("syntax".equals(source)
                || code.startsWith("syntax-")
                || code.equals("parse-error")
                || code.equals("MISSING_ASSIGNMENT")
                || code.equals("INVALID_STATEMENT")
                || code.equals("UNCLOSED_BRACKET")
                || code.equals("UNEXPECTED_TOKEN")) {
            return "malformed-syntax";
        }
        if ("root-missing".equals(code) || "root-not-renderable".equals(code)) {
            return "root-missing";
        }
        if ("unresolved-ref".equals(code) || "orphan-statement".equals(code) || "reference".equals(source)) {
            return "reference-graph";
        }
        if ("unknown-component".equals(code)) {
            return "hallucinated-component";
        }
        if ("missing-required".equals(code) || "null-required".equals(code)) {
            return "required-field";
        }
        if (code.startsWith("type-data-")
                || code.equals("type-field-missing")
                || code.equals("type-table-column-missing")
                || code.equals("type-table-row-shape-mismatch")
                || code.startsWith("type-chart-")
                || code.startsWith("builtin-argument-")
                || code.equals("type-operator-mismatch")) {
            return "data-model-error";
        }
        if ("invalid-prop".equals(code)
                && (msg.contains("one of") || msg.contains("enum") || msg.contains("枚举"))) {
            return "enum-mismatch";
        }
        if ("excess-args".equals(code)
                || "invalid-prop".equals(code)
                || "type-prop-mismatch".equals(code)
                || "component-slot-type-mismatch".equals(code)
                || "empty-component-slot".equals(code)
                || "inline-reserved".equals(code)) {
            return "signature-mismatch";
        }
        return "other";
    }

    private static String buildDetail(ValidationIssue issue) {
        StringBuilder sb = new StringBuilder();
        if (issue.code() != null) {
            sb.append(issue.code());
        }
        if (issue.component() != null && !issue.component().isEmpty()) {
            sb.append(":").append(issue.component());
        }
        if (issue.path() != null && !issue.path().isEmpty()) {
            sb.append(issue.path());
        }
        return sb.toString();
    }

    private static int countReachableComponents(Program program, String entryId) {
        if (entryId == null || program == null) {
            return 0;
        }
        Map<String, Statement> byId = program.currentById();
        Statement rootStmt = byId.get(entryId);
        if (rootStmt == null) {
            return 0;
        }
        Set<AstNode.Comp> comps = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> visitedStmts = new HashSet<>();
        visitedStmts.add(entryId);
        walk(rootStmt.expr(), byId, visitedStmts, comps);
        return comps.size();
    }

    private static void walk(
            AstNode node,
            Map<String, Statement> byId,
            Set<String> visitedStmts,
            Set<AstNode.Comp> comps) {
        if (node == null) {
            return;
        }
        switch (node) {
            case AstNode.Comp comp -> {
                if (comp.name() != null && !comp.name().startsWith("@")) {
                    comps.add(comp);
                }
                for (AstNode arg : comp.args()) {
                    walk(arg, byId, visitedStmts, comps);
                }
            }
            case AstNode.Arr arr -> {
                for (AstNode el : arr.els()) {
                    walk(el, byId, visitedStmts, comps);
                }
            }
            case AstNode.Obj obj -> {
                for (AstNode.Obj.Entry e : obj.entries()) {
                    walk(e.value(), byId, visitedStmts, comps);
                }
            }
            case AstNode.Ref ref -> {
                String target = ref.n();
                if (target != null && byId.containsKey(target) && visitedStmts.add(target)) {
                    walk(byId.get(target).expr(), byId, visitedStmts, comps);
                }
            }
            case AstNode.BinOp bin -> {
                walk(bin.left(), byId, visitedStmts, comps);
                walk(bin.right(), byId, visitedStmts, comps);
            }
            case AstNode.UnaryOp un -> walk(un.operand(), byId, visitedStmts, comps);
            case AstNode.Ternary ter -> {
                walk(ter.cond(), byId, visitedStmts, comps);
                walk(ter.then(), byId, visitedStmts, comps);
                walk(ter.otherwise(), byId, visitedStmts, comps);
            }
            case AstNode.Member mem -> walk(mem.obj(), byId, visitedStmts, comps);
            case AstNode.Index idx -> {
                walk(idx.obj(), byId, visitedStmts, comps);
                walk(idx.index(), byId, visitedStmts, comps);
            }
            case AstNode.Assign assign -> walk(assign.value(), byId, visitedStmts, comps);
            default -> {}
        }
    }

    private static String stripThink(String text) {
        if (text == null) {
            return "";
        }
        return text.contains("</think>") ? THINK_BLOCK.matcher(text).replaceFirst("") : text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asOptionalMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object for dataModel");
    }
}
