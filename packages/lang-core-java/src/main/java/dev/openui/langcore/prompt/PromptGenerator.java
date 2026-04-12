package dev.openui.langcore.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates an LLM system-prompt string from a {@link PromptSpec}.
 *
 * <p>Pure string construction — no external templating library.
 * Direct translation of {@code parser/prompt.ts :: generatePrompt}.
 *
 * Ref: Design §11
 */
public final class PromptGenerator {

    private static final String DEFAULT_PREAMBLE =
            "You are an AI assistant that responds using openui-lang, a declarative UI language. " +
            "Your ENTIRE response must be valid openui-lang code — no markdown, no explanations, just openui-lang.";

    private PromptGenerator() {}

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static String generatePrompt(PromptSpec spec) {
        String rootName = spec.root() != null ? spec.root() : "Root";
        boolean hasTools = spec.tools() != null && !spec.tools().isEmpty();

        boolean toolCalls = spec.toolCalls() || hasTools;
        boolean bindings  = spec.bindings()  || toolCalls;
        boolean supportsExpressions = toolCalls || bindings;

        boolean usesActionExpression = spec.components().values().stream()
                .anyMatch(c -> c.signature() != null && c.signature().contains("ActionExpression"));

        List<String> parts = new ArrayList<>();

        parts.add(spec.preamble() != null ? spec.preamble() : DEFAULT_PREAMBLE);
        parts.add("");
        parts.add(syntaxRules(rootName, supportsExpressions, bindings));
        parts.add("");
        parts.add(generateComponentSignatures(spec, toolCalls, bindings, usesActionExpression));

        if (supportsExpressions) {
            parts.add("");
            parts.add(builtinFunctionsSection());
        }

        if (toolCalls) {
            parts.add("");
            parts.add(querySection());
            parts.add("");
            parts.add(mutationSection());
        }

        if (usesActionExpression) {
            parts.add("");
            parts.add(actionSection(toolCalls, bindings));
        }

        if (toolCalls && bindings) {
            parts.add("");
            parts.add(interactiveFiltersSection());
        }

        if (toolCalls) {
            parts.add("");
            parts.add(toolWorkflowSection());
        }

        if (hasTools) {
            parts.add("");
            parts.add(renderToolsSection(spec.tools()));
        }

        parts.add("");
        parts.add(streamingRules(rootName, supportsExpressions));

        List<String> allExamples = new ArrayList<>();
        if (spec.examples() != null)     allExamples.addAll(spec.examples());
        if (spec.toolExamples() != null) allExamples.addAll(spec.toolExamples());
        if (!allExamples.isEmpty()) {
            parts.add("");
            parts.add("## Examples");
            parts.add("");
            for (String ex : allExamples) {
                parts.add(ex);
                parts.add("");
            }
        }

        if (spec.editMode()) {
            parts.add("");
            parts.add(editModeSection());
        }

        if (spec.inlineMode()) {
            parts.add("");
            parts.add(inlineModeSection());
        }

        parts.add(importantRules(rootName, toolCalls, bindings));

        if (spec.additionalRules() != null && !spec.additionalRules().isEmpty()) {
            parts.add("");
            for (String rule : spec.additionalRules()) {
                parts.add("- " + rule);
            }
        }

        return String.join("\n", parts);
    }

    // -------------------------------------------------------------------------
    // Section generators
    // -------------------------------------------------------------------------

    private static String syntaxRules(String rootName, boolean supportsExpressions, boolean bindings) {
        List<String> lines = new ArrayList<>(List.of(
            "## Syntax Rules",
            "",
            "1. Each statement is on its own line: `identifier = Expression`",
            "2. `root` is the entry point — every program must define `root = " + rootName + "(...)`",
            "3. Expressions are: strings (\"...\"), numbers, booleans (true/false), null, arrays ([...]), objects ({...}), or component calls TypeName(arg1, arg2, ...)",
            "4. Use references for readability: define `name = ...` on one line, then use `name` later",
            "5. EVERY variable (except root) MUST be referenced by at least one other variable. Unreferenced variables are silently dropped and will NOT render. Always include defined variables in their parent's children/items array.",
            "6. Arguments are POSITIONAL (order matters, not names). Write `Stack([children], \"row\", \"l\")` NOT `Stack([children], direction: \"row\", gap: \"l\")` — colon syntax is NOT supported and silently breaks",
            "7. Optional arguments can be omitted from the end"
        ));

        int ruleNum = 8;
        if (bindings) {
            lines.add(ruleNum++ + ". Declare mutable state with `$varName = defaultValue`. Components marked with `$binding` can read/write these. Undeclared $variables are auto-created with null default.");
        }
        if (supportsExpressions) {
            lines.add(ruleNum++ + ". String concatenation: `\"text\" + $var + \"more\"`");
            lines.add(ruleNum++ + ". Dot member access: `query.field` reads a field; on arrays it extracts that field from every element");
            lines.add(ruleNum++ + ". Index access: `arr[0]`, `data[index]`");
            lines.add(ruleNum++ + ". Arithmetic operators: +, -, *, /, % (work on numbers; + is string concat when either side is a string)");
            lines.add(ruleNum++ + ". Comparison: ==, !=, >, <, >=, <=");
            lines.add(ruleNum++ + ". Logical: &&, ||, ! (prefix)");
            lines.add(ruleNum++ + ". Ternary: `condition ? valueIfTrue : valueIfFalse`");
            lines.add(ruleNum++ + ". Parentheses for grouping: `(a + b) * c`");
        }
        lines.add("- Strings use double quotes with backslash escaping");
        return String.join("\n", lines);
    }

    private static String builtinFunctionsSection() {
        return """
                ## Built-in Functions

                Data functions prefixed with `@` to distinguish from components. These are the ONLY functions available — do NOT invent new ones.
                Use @-prefixed built-in functions (@Count, @Sum, @Avg, @Min, @Max, @Round) on Query results — do NOT hardcode computed values.

                @Count(array) — Returns the number of items in an array
                @First(array) — Returns the first item in an array
                @Last(array) — Returns the last item in an array
                @Sum(array) — Returns the sum of all numbers in an array
                @Avg(array) — Returns the average of all numbers in an array
                @Min(array) — Returns the minimum number in an array
                @Max(array) — Returns the maximum number in an array
                @Sort(array, field?, direction?) — Sorts an array by a field (asc by default, pass "desc" for descending)
                @Filter(array, field, operator, value) — Filters an array by a field comparison (==, !=, >, <, >=, <=, contains)
                @Round(number, decimals?) — Rounds a number to the specified decimal places
                @Abs(number) — Returns the absolute value of a number
                @Floor(number) — Rounds down to the nearest integer
                @Ceil(number) — Rounds up to the nearest integer
                @Each(array, varName, template) — Maps each item in an array to a component

                Builtins compose — output of one is input to the next:
                `@Count(@Filter(data.rows, "field", "==", "val"))` for KPIs/chart values, `@Round(@Avg(data.rows.score), 1)`, `@Each(data.rows, "item", Comp(item.field))` for per-item rendering.
                Array pluck: `data.rows.field` extracts a field from every row → use with @Sum, @Avg, charts, tables.

                IMPORTANT @Each rule: The loop variable (e.g. "item") is ONLY available inside the @Each template expression. Always inline the template — do NOT extract it to a separate statement.
                CORRECT: `Col("Actions", @Each(rows, "t", Button("Edit", Action([@Set($id, t.id)]))))`
                WRONG: `myBtn = Button("Edit", Action([@Set($id, t.id)]))` then `Col("Actions", @Each(rows, "t", myBtn))` — t is undefined in myBtn.""";
    }

    private static String querySection() {
        return """
                ## Query — Live Data Fetching

                Fetch data from available tools. Returns defaults instantly, swaps in real data when it arrives.

                ```
                metrics = Query("tool_name", {arg1: value, arg2: $binding}, {defaultField: 0, defaultData: []}, refreshInterval?)
                ```

                - First arg: tool name (string)
                - Second arg: arguments object (may reference $bindings — re-fetches automatically on change)
                - Third arg: default data (rendered immediately before fetch resolves)
                - Fourth arg (optional): refresh interval in seconds (e.g. 30 for auto-refresh every 30s)
                - Use dot access on results: metrics.totalEvents, metrics.data.day (array pluck)
                - Query results must use regular identifiers: `metrics = Query(...)`, NOT `$metrics = Query(...)`
                - Manual refresh: `Button("Refresh", Action([@Run(query1), @Run(query2)]), "secondary")` — re-fetches the listed queries
                - Refresh all queries: create Action with @Run for each query""";
    }

    private static String mutationSection() {
        return """
                ## Mutation — Write Operations

                Execute state-changing tool calls (create, update, delete). Unlike Query (auto-fetches on render), Mutation fires only on button click via Action.

                ```
                result = Mutation("tool_name", {arg1: $binding, arg2: "value"})
                ```

                - First arg: tool name (string)
                - Second arg: arguments object (evaluated with current $binding values at click time)
                - result.status: "idle" | "loading" | "success" | "error"
                - result.data: tool response on success
                - result.error: error message on failure
                - Mutation results use regular identifiers: `result = Mutation(...)`, NOT `$result`
                - Show loading state: `result.status == "loading" ? TextContent("Saving...") : null`""";
    }

    private static String actionSection(boolean toolCalls, boolean bindings) {
        List<String> steps = new ArrayList<>();
        if (toolCalls) steps.add("- @Run(queryOrMutationRef) — Execute a Mutation or re-fetch a Query (ref must be a declared Query/Mutation)");
        steps.add("- @ToAssistant(\"message\") — Send a message to the assistant (for conversational buttons like \"Tell me more\", \"Explain this\")");
        steps.add("- @OpenUrl(\"https://...\") — Navigate to a URL");
        if (bindings) {
            steps.add("- @Set($variable, value) — Set a $variable to a specific value");
            steps.add("- @Reset($var1, $var2, ...) — Reset $variables to their declared defaults");
        }

        List<String> examples = new ArrayList<>();
        if (toolCalls) {
            examples.add("""
                    Example — mutation + refresh + reset (PREFERRED pattern):
                    ```
                    $binding = "default"
                    result = Mutation("tool_name", {field: $binding})
                    data = Query("tool_name", {}, {rows: []})
                    onSubmit = Action([@Run(result), @Run(data), @Reset($binding)])
                    ```""");
        }
        examples.add("""
                Example — simple nav:
                ```
                viewBtn = Button("View", Action([@OpenUrl("https://example.com")]))
                ```""");

        List<String> rules = new ArrayList<>();
        rules.add("- Action can be assigned to a variable or inlined: Button(\"Go\", onSubmit) and Button(\"Go\", Action([...])) both work");
        if (toolCalls) {
            rules.add("- If a @Run(mutation) step fails, remaining steps are skipped (halt on failure)");
            rules.add("- @Run(queryRef) re-fetches the query (fire-and-forget, cannot fail)");
        }

        return "## Action — Button Behavior\n\n" +
               "Action([@steps...]) wires button clicks to operations. Steps are @-prefixed built-in actions. Steps execute in order.\n" +
               "Buttons without an explicit Action prop automatically send their label to the assistant (equivalent to Action([@ToAssistant(label)])).\n\n" +
               "Available steps:\n" + String.join("\n", steps) + "\n\n" +
               String.join("\n\n", examples) + "\n\n" +
               String.join("\n", rules);
    }

    private static String interactiveFiltersSection() {
        return """
                ## Interactive Filters

                To let the user filter data with a dropdown:
                1. Declare a $variable with a default: `$dateRange = "14"`
                2. Create a Select with name, items, and binding: `Select("dateRange", [...], null, null, $dateRange)`
                3. Wrap in FormControl for a label: `FormControl("Date Range", Select(...))`
                4. Pass $dateRange in Query args: `Query("tool", {dateRange: $dateRange}, {defaults})`
                5. When the user changes the Select, $dateRange updates and the Query automatically re-fetches

                FILTER WIRING RULE: If a $binding filter is visible in the UI, EVERY relevant Query MUST reference that $binding in its args.

                Rules for $variables:
                - $variables hold simple values (strings or numbers), NOT arrays or objects
                - $variables must be bound to a Select/Input component via the value argument to be interactive
                - Queries must use regular identifiers (NOT $variables): `metrics = Query(...)` not `$metrics = Query(...)`""";
    }

    private static String toolWorkflowSection() {
        return """
                ## Data Workflow

                When tools are available, follow this workflow:
                1. FIRST: Call the most relevant tool to inspect the real data shape before generating code
                2. Use Query() for READ operations (data that should stay live) — NEVER hardcode tool results as literal arrays or objects
                3. Use Mutation() for WRITE operations (create, update, delete) — triggered by button clicks via Action([@Run(mutationRef)])
                4. Use the real data from step 1 as condensed Query defaults (3-5 rows) so the UI renders immediately
                5. Use @-prefixed builtins (@Count, @Filter, @Sort, @Sum) on Query results for KPIs and aggregations
                6. Hardcoded arrays are ONLY for static display data (labels, options) where no tool exists""";
    }

    private static String editModeSection() {
        return """
                ## Edit Mode

                The runtime merges by statement name: same name = replace, new name = append.
                Output ONLY statements that changed or are new. Everything else is kept automatically.

                ### Delete
                To remove a component, update the parent to exclude it from its children array. Orphaned statements are automatically garbage-collected.

                ### Rules
                - Reuse existing statement names exactly — do not rename
                - Do NOT re-emit unchanged statements — the runtime keeps them
                - A typical edit patch is 1-10 statements, not 20+
                - NEVER output the entire program as a patch. Only output what actually changes""";
    }

    private static String inlineModeSection() {
        return """
                ## Inline Mode

                You are in inline mode. You can respond in two ways:

                ### 1. Code response (when the user wants to CREATE or CHANGE the UI)
                Wrap openui-lang code in triple-backtick fences. You can include explanatory text before/after.

                ### 2. Text-only response (when the user asks a QUESTION)
                If the user asks "what is this?", "explain the chart", etc. — respond with plain text. Do NOT output any openui-lang code.

                ### Rules
                - When the user asks for changes, output ONLY the changed/new statements in a fenced block
                - When the user asks a question, respond with text only — NO code. The dashboard stays unchanged.""";
    }

    private static String streamingRules(String rootName, boolean supportsExpressions) {
        List<String> steps = new ArrayList<>();
        steps.add("1. `root = " + rootName + "(...)` — UI shell appears immediately");
        if (supportsExpressions) {
            steps.add("2. $variable declarations — state ready for bindings");
            steps.add("3. Query statements — defaults resolve immediately so components render with data");
            steps.add("4. Component definitions — fill in with data already available");
            steps.add("5. Data values — leaf content last");
        } else {
            steps.add("2. Component definitions — fill in as they stream");
            steps.add("3. Data values — leaf content last");
        }

        return "## Hoisting & Streaming (CRITICAL)\n\n" +
               "openui-lang supports hoisting: a reference can be used BEFORE it is defined.\n\n" +
               "**Recommended statement order for optimal streaming:**\n" +
               String.join("\n", steps) + "\n\n" +
               "Always write the root = " + rootName + "(...) statement first so the UI shell appears immediately.";
    }

    private static String importantRules(String rootName, boolean toolCalls, boolean bindings) {
        List<String> verifyLines = new ArrayList<>();
        verifyLines.add("1. root = " + rootName + "(...) is the FIRST line (for optimal streaming).");
        verifyLines.add("2. Every referenced name is defined. Every defined name (other than root) is reachable from root.");
        if (toolCalls) verifyLines.add("3. Every Query result is referenced by at least one component.");
        if (bindings)  verifyLines.add((toolCalls ? "4" : "3") + ". Every $binding appears in at least one component or expression.");

        return "## Important Rules\n" +
               "- When asked about data, generate realistic/plausible data\n" +
               "- Choose components that best represent the content\n\n" +
               "## Final Verification\n" +
               "Before finishing, walk your output and verify:\n" +
               String.join("\n", verifyLines);
    }

    // -------------------------------------------------------------------------
    // Component signatures section
    // -------------------------------------------------------------------------

    private static String generateComponentSignatures(PromptSpec spec, boolean toolCalls,
                                                       boolean bindings, boolean usesActionExpression) {
        List<String> lines = new ArrayList<>(List.of(
            "## Component Signatures",
            "",
            "Arguments marked with ? are optional. Sub-components can be inline or referenced; prefer references for better streaming."
        ));

        if (usesActionExpression) {
            List<String> allSteps = new ArrayList<>();
            if (toolCalls) allSteps.add("@Run");
            allSteps.add("@ToAssistant");
            allSteps.add("@OpenUrl");
            if (bindings) { allSteps.add("@Set"); allSteps.add("@Reset"); }
            lines.add("Props typed `ActionExpression` accept an Action([@steps...]) expression. See the Action section for available steps (" + String.join(", ", allSteps) + ").");
        }

        boolean usesBindings = bindings || spec.components().values().stream()
                .anyMatch(c -> c.signature() != null && c.signature().contains("$binding"));
        if (usesBindings) {
            lines.add("Props marked `$binding<type>` accept a `$variable` reference for two-way binding.");
        }

        if (spec.componentGroups() != null && !spec.componentGroups().isEmpty()) {
            java.util.Set<String> grouped = new java.util.LinkedHashSet<>();
            for (ComponentGroup group : spec.componentGroups()) {
                lines.add("");
                lines.add("### " + group.name());
                for (String name : group.components()) {
                    if (grouped.contains(name)) continue;
                    ComponentPromptSpec comp = spec.components().get(name);
                    if (comp == null) continue;
                    grouped.add(name);
                    lines.add(formatSig(comp));
                }
                if (group.notes() != null) group.notes().forEach(lines::add);
            }
            List<String> ungrouped = spec.components().keySet().stream()
                    .filter(n -> !grouped.contains(n)).toList();
            if (!ungrouped.isEmpty()) {
                lines.add("");
                lines.add("### Other");
                for (String name : ungrouped) lines.add(formatSig(spec.components().get(name)));
            }
        } else {
            lines.add("");
            for (ComponentPromptSpec comp : spec.components().values()) lines.add(formatSig(comp));
        }

        return String.join("\n", lines);
    }

    private static String formatSig(ComponentPromptSpec comp) {
        return comp.description() != null && !comp.description().isEmpty()
                ? comp.signature() + " — " + comp.description()
                : comp.signature();
    }

    // -------------------------------------------------------------------------
    // Tools section
    // -------------------------------------------------------------------------

    private static String renderToolsSection(List<Object> tools) {
        List<String> lines = new ArrayList<>(List.of(
            "## Available Tools",
            "",
            "Use these with Query() for read operations or Mutation() for write operations.",
            ""
        ));

        List<ToolSpec> specTools = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof String s) {
                lines.add("- " + s);
            } else if (tool instanceof ToolSpec ts) {
                specTools.add(ts);
                lines.add(renderToolSignature(ts));
            }
        }

        List<ToolSpec> withOutput = specTools.stream().filter(t -> t.outputSchema() != null).toList();
        if (!withOutput.isEmpty()) {
            lines.add("");
            lines.add("### Default values for Query results");
            lines.add("");
            lines.add("Use these shapes as minimal Query defaults:");
            for (ToolSpec t : withOutput) {
                lines.add("- " + t.name() + ": `" + defaultForSchema(t.outputSchema()) + "`");
            }
        }

        lines.add("");
        lines.add("CRITICAL: Use ONLY the tools listed above in Query() and Mutation() calls. Do NOT invent or guess tool names.");
        return String.join("\n", lines);
    }

    private static String renderToolSignature(ToolSpec tool) {
        StringBuilder args = new StringBuilder();
        if (tool.inputSchema() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = tool.inputSchema().get("required") instanceof List<?> r
                    ? (List<String>) r : List.of();
            if (props != null && !props.isEmpty()) {
                List<String> argParts = new ArrayList<>();
                for (Map.Entry<String, Object> e : props.entrySet()) {
                    String opt = required.contains(e.getKey()) ? "" : "?";
                    @SuppressWarnings("unchecked")
                    String typeStr = jsonSchemaTypeStr((Map<String, Object>) e.getValue());
                    argParts.add(e.getKey() + opt + ": " + typeStr);
                }
                args.append(String.join(", ", argParts));
            }
        }

        String returnType = tool.outputSchema() != null
                ? " → " + jsonSchemaTypeStr(tool.outputSchema()) : "";

        String line = "- " + tool.name() + "(" + args + ")" + returnType;
        if (tool.description() != null) line += "\n  " + tool.description();
        return line;
    }

    // -------------------------------------------------------------------------
    // jsonSchemaTypeStr helper
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static String jsonSchemaTypeStr(Map<String, Object> schema) {
        if (schema == null) return "any";
        String type = schema.get("type") instanceof String s ? s : null;

        if ("string".equals(type)) {
            Object enumVals = schema.get("enum");
            if (enumVals instanceof List<?> list && !list.isEmpty()) {
                List<String> quoted = new ArrayList<>();
                for (Object v : list) quoted.add("\"" + v + "\"");
                return String.join(" | ", quoted);
            }
            return "string";
        }
        if ("number".equals(type) || "integer".equals(type)) return "number";
        if ("boolean".equals(type)) return "boolean";
        if ("array".equals(type)) {
            Object items = schema.get("items");
            if (items instanceof Map<?, ?> itemSchema) {
                return jsonSchemaTypeStr((Map<String, Object>) itemSchema) + "[]";
            }
            return "any[]";
        }
        if ("object".equals(type)) {
            Object propsObj = schema.get("properties");
            if (propsObj instanceof Map<?, ?> propsMap && !propsMap.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> reqList = schema.get("required") instanceof List<?> r
                        ? (List<String>) r : List.of();
                List<String> fields = new ArrayList<>();
                for (Map.Entry<?, ?> e : propsMap.entrySet()) {
                    String k   = String.valueOf(e.getKey());
                    String opt = reqList.contains(k) ? "" : "?";
                    @SuppressWarnings("unchecked")
                    String valType = jsonSchemaTypeStr((Map<String, Object>) e.getValue());
                    fields.add(k + opt + ": " + valType);
                }
                return "{" + String.join(", ", fields) + "}";
            }
            return "object";
        }
        return "any";
    }

    // -------------------------------------------------------------------------
    // defaultForSchema helper
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object defaultForSchema(Map<String, Object> schema) {
        if (schema == null) return null;
        String type = schema.get("type") instanceof String s ? s : null;
        if ("string".equals(type))  return "";
        if ("number".equals(type) || "integer".equals(type)) return 0;
        if ("boolean".equals(type)) return false;
        if ("array".equals(type))   return List.of();
        if ("object".equals(type)) {
            Object propsObj = schema.get("properties");
            if (propsObj instanceof Map<?, ?> propsMap && !propsMap.isEmpty()) {
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> e : propsMap.entrySet()) {
                    result.put(String.valueOf(e.getKey()),
                            defaultForSchema((Map<String, Object>) e.getValue()));
                }
                return result;
            }
            return Map.of();
        }
        return null;
    }
}
