package com.huawei.cloudsop.genui.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Byte-for-byte Java port of {@code packages/lang-core/src/parser/prompt.ts}'s {@code generatePrompt}.
 *
 * <p>Per design D9, the Java SDK is the authoritative prompt assembler. Builtin docs (Template/Data
 * Built-ins) are sourced from the {@link BuiltinSpec} manifest exported by the frontend; every other
 * section is transcribed from {@code prompt.ts}. Cross-language golden tests pin the two outputs to
 * byte equality on the merged-{@code PromptSpec} layer.
 */
final class PromptAssembler {
  private static final String PREAMBLE =
      "You are an AI assistant that responds using openui-lang, a declarative UI language. "
          + "Your ENTIRE response must be valid openui-lang code — no markdown, no explanations, just openui-lang.";

  private PromptAssembler() {}

  static String assemble(PromptInput input, List<BuiltinSpec> builtins) {
    String rootName = input.root() == null ? "Root" : input.root();
    boolean hasTools = input.tools() != null && !input.tools().isEmpty();
    boolean toolCalls = input.toolCalls() == null ? hasTools : input.toolCalls();
    boolean bindings = input.bindings() == null ? toolCalls : input.bindings();
    boolean supportsExpressions = toolCalls || bindings;
    boolean usesActionExpression =
        input.components().values().stream()
            .anyMatch(c -> c.signature() != null && c.signature().contains("ActionExpression"));
    boolean hasRawDataModel = hasRawDataModel(input.dataModel());

    ArrayList<String> parts = new ArrayList<>();
    parts.add(input.preamble() != null ? input.preamble() : PREAMBLE);
    parts.add("");
    parts.add(syntaxRules(rootName, bindings));
    parts.add("");
    parts.add(
        componentSignatures(
            input.components(), input.componentGroups(), toolCalls, bindings, usesActionExpression));

    if (hasRawDataModel) {
      parts.add("");
      parts.add(dataModelSection(input.dataModel()));
    }

    parts.add("");
    parts.add(templateBuiltinsSection(builtins));

    if (supportsExpressions || hasRawDataModel) {
      parts.add("");
      parts.add(dataBuiltinsSection(builtins));
    }

    if (toolCalls) {
      parts.add("");
      parts.add(QUERY_SECTION);
      parts.add("");
      parts.add(MUTATION_SECTION);
    }

    if (usesActionExpression) {
      parts.add("");
      parts.add(actionSection(toolCalls, bindings));
    }

    if (toolCalls && bindings) {
      parts.add("");
      parts.add(INTERACTIVE_FILTERS_SECTION);
    }

    if (toolCalls) {
      parts.add("");
      parts.add(TOOL_WORKFLOW_SECTION);
    }

    if (hasTools) {
      parts.add("");
      parts.add(renderToolsSection(input.tools()));
    }

    parts.add("");
    parts.add(streamingRules(rootName, supportsExpressions));

    if (!input.examples().isEmpty()) {
      parts.add("");
      parts.add("## Examples");
      parts.add("");
      for (String example : input.examples()) {
        parts.add(example);
        parts.add("");
      }
    }

    if (Boolean.TRUE.equals(input.editMode())) {
      parts.add("");
      parts.add(EDIT_MODE_SECTION);
    }

    if (Boolean.TRUE.equals(input.inlineMode())) {
      parts.add("");
      parts.add(INLINE_MODE_SECTION);
    }

    parts.add(importantRules(rootName, toolCalls, bindings));

    if (!input.additionalRules().isEmpty()) {
      parts.add("");
      for (String rule : input.additionalRules()) {
        parts.add("- " + rule);
      }
    }

    return String.join("\n", parts);
  }

  // ─── Syntax rules ──────────────────────────────────────────────────────────

  private static String syntaxRules(String rootName, boolean bindings) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("## Syntax Rules");
    lines.add("");
    lines.add("1. Each statement is on its own line: `identifier = Expression`");
    lines.add("2. `root` is the entry point — every program must define `root = " + rootName + "(...)`");
    lines.add(
        "3. Expressions are: strings (\"...\"), numbers, booleans (true/false), null, arrays ([...]), objects ({...}), or component calls TypeName(arg1, arg2, ...)");
    lines.add("4. Use references for readability: define `name = ...` on one line, then use `name` later");
    lines.add(
        "5. EVERY variable (except root) MUST be referenced by at least one other variable. Unreferenced variables are silently dropped and will NOT render. Always include defined variables in their parent's children/items array.");
    lines.add(
        "6. Arguments are POSITIONAL (order matters, not names). Write `Stack([children], \"row\", \"l\")` NOT `Stack([children], direction: \"row\", gap: \"l\")` — colon syntax is NOT supported and silently breaks");
    lines.add("7. Optional arguments can be omitted from the end");

    int ruleNum = 8;
    if (bindings) {
      lines.add(
          ruleNum++
              + ". Declare mutable state with `$varName = defaultValue`. Components marked with `$binding` can read/write these. Undeclared $variables are auto-created with null default.");
    }
    lines.add(ruleNum++ + ". String concatenation: `\"text\" + value + \"more\"`");
    lines.add(
        ruleNum++
            + ". Dot member access: `obj.field` reads a field; on arrays it extracts that field from every element");
    lines.add(ruleNum++ + ". Index access: `arr[0]`, `items[index]`");
    lines.add(
        ruleNum++
            + ". Arithmetic operators: +, -, *, /, % (work on numbers; + is string concat when either side is a string)");
    lines.add(ruleNum++ + ". Comparison: ==, !=, >, <, >=, <=");
    lines.add(ruleNum++ + ". Logical: &&, ||, ! (prefix)");
    lines.add(ruleNum++ + ". Ternary: `condition ? valueIfTrue : valueIfFalse`");
    lines.add(ruleNum++ + ". Parentheses for grouping: `(a + b) * c`");
    lines.add("- Strings use double quotes with backslash escaping");

    return String.join("\n", lines);
  }

  // ─── Component signatures ──────────────────────────────────────────────────

  private static String componentSignatures(
      Map<String, ComponentPromptSpec> components,
      List<ComponentGroup> componentGroups,
      boolean toolCalls,
      boolean bindings,
      boolean usesActionExpression) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("## Component Signatures");
    lines.add("");
    lines.add(
        "Arguments marked with ? are optional. Sub-components can be inline or referenced; prefer references for better streaming.");

    if (usesActionExpression) {
      ArrayList<String> allSteps = new ArrayList<>();
      if (toolCalls) allSteps.add("@Run");
      allSteps.add("@ToAssistant");
      allSteps.add("@OpenUrl");
      if (bindings) {
        allSteps.add("@Set");
        allSteps.add("@Reset");
      }
      lines.add(
          "Props typed `ActionExpression` accept an Action([@steps...]) expression. See the Action section for available steps ("
              + String.join(", ", allSteps)
              + ").");
    }

    boolean usesBindings =
        bindings
            || components.values().stream()
                .anyMatch(c -> c.signature() != null && c.signature().contains("$binding"));
    if (usesBindings) {
      lines.add("Props marked `$binding<type>` accept a `$variable` reference for two-way binding.");
    }

    if (componentGroups != null && !componentGroups.isEmpty()) {
      Set<String> grouped = new LinkedHashSet<>();
      for (ComponentGroup group : componentGroups) {
        lines.add("");
        lines.add("### " + group.name());
        for (String name : group.components()) {
          if (grouped.contains(name)) continue;
          ComponentPromptSpec comp = components.get(name);
          if (comp == null) continue;
          grouped.add(name);
          lines.add(formatSig(comp));
        }
        if (group.notes() != null && !group.notes().isEmpty()) {
          for (String note : group.notes()) lines.add(note);
        }
      }
      ArrayList<String> ungrouped = new ArrayList<>();
      for (String name : components.keySet()) {
        if (!grouped.contains(name)) ungrouped.add(name);
      }
      if (!ungrouped.isEmpty()) {
        lines.add("");
        lines.add("### Other");
        for (String name : ungrouped) lines.add(formatSig(components.get(name)));
      }
    } else {
      lines.add("");
      for (ComponentPromptSpec comp : components.values()) lines.add(formatSig(comp));
    }
    return String.join("\n", lines);
  }

  private static String formatSig(ComponentPromptSpec comp) {
    String description = comp.description();
    if (description == null || description.isEmpty()) return comp.signature();
    return comp.signature() + " — " + description;
  }

  // ─── Data model ────────────────────────────────────────────────────────────

  private static boolean hasRawDataModel(DataModelSpec dataModel) {
    return dataModel != null && dataModel.raw() != null && !dataModel.raw().isEmpty();
  }

  private static String dataModelSection(DataModelSpec dataModel) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("## Data Model");
    lines.add("");
    if (dataModel.description() != null && !dataModel.description().isEmpty()) {
      lines.add(dataModel.description());
      lines.add("");
    }
    lines.add("The following host data is available via `data.<field>`:");
    lines.add("");
    lines.add("```json");
    lines.add(Json.stringifyPretty(dataModel.raw()));
    lines.add("```");
    lines.add("");
    lines.add("Use `data.<field>` to read host data.");
    lines.add("Use `Each(...)` to iterate arrays.");
    lines.add("Array pluck works on arrays: `data.sales.revenue`.");
    return String.join("\n", lines);
  }

  // ─── Builtins (from manifest) ──────────────────────────────────────────────

  private static String templateBuiltinsSection(List<BuiltinSpec> builtins) {
    String entries = builtinLines(builtins, true);
    return "## Template Built-ins\n"
        + "\n"
        + "Template/render functions are prefixed with `@` to distinguish them from components. These are available in every prompt configuration.\n"
        + "\n"
        + entries
        + "\n"
        + "\n"
        + "Builtins compose — output of one is input to the next:\n"
        + "`@Each(data.rows, \"item\", Comp(@Switch(item.status, {\"open\": \"Open\"}, \"Unknown\")))` for per-item rendering and enum display.\n"
        + "\n"
        + "IMPORTANT @Each rule: The loop variable (e.g. \"item\") is ONLY available inside the @Each template expression. Always inline the template — do NOT extract it to a separate statement.\n"
        + "CORRECT: `Col(\"Actions\", @Each(rows, \"t\", Button(\"Edit\", Action([@Set($id, t.id)]))))`\n"
        + "WRONG: `myBtn = Button(\"Edit\", Action([@Set($id, t.id)]))` then `Col(\"Actions\", @Each(rows, \"t\", myBtn))` — t is undefined in myBtn.\n"
        + "\n"
        + "IMPORTANT @Render rule: Use `@Render(\"v\", expr)` or `@Render(\"v\", \"row\", expr)` as a prop value when a component expects a render function (for example, a table cell renderer). `@Render` outside a prop context renders as null.";
  }

  private static String dataBuiltinsSection(List<BuiltinSpec> builtins) {
    String entries = builtinLines(builtins, false);
    return "## Data Built-ins\n"
        + "\n"
        + "Data functions prefixed with `@` are available when expressions are enabled. These are the ONLY data functions available — do NOT invent new ones.\n"
        + "Use @-prefixed built-in functions (@Count, @Sum, @Avg, @Min, @Max, @Round) on Query results — do NOT hardcode computed values.\n"
        + "\n"
        + entries
        + "\n"
        + "\n"
        + "Builtins compose — output of one is input to the next:\n"
        + "`@Count(@Filter(data.rows, \"field\", \"==\", \"val\"))` for KPIs/chart values, `@Round(@Avg(data.rows.score), 1)`.\n"
        + "Array pluck: `data.rows.field` extracts a field from every row → use with @Sum, @Avg, charts, tables.";
  }

  private static String builtinLines(List<BuiltinSpec> builtins, boolean templateBuiltin) {
    ArrayList<String> lines = new ArrayList<>();
    for (BuiltinSpec builtin : builtins) {
      if (builtin.templateBuiltin() == templateBuiltin) {
        lines.add(formatBuiltinSignature(builtin.signature()) + " — " + builtin.description());
      }
    }
    return String.join("\n", lines);
  }

  private static String formatBuiltinSignature(String signature) {
    String[] variants = signature.split(" / ");
    ArrayList<String> prefixed = new ArrayList<>();
    for (String variant : variants) prefixed.add("@" + variant);
    return String.join(" / ", prefixed);
  }

  // ─── Action ────────────────────────────────────────────────────────────────

  private static String actionSection(boolean toolCalls, boolean bindings) {
    ArrayList<String> steps = new ArrayList<>();
    if (toolCalls) {
      steps.add(
          "- @Run(queryOrMutationRef) — Execute a Mutation or re-fetch a Query (ref must be a declared Query/Mutation)");
    }
    steps.add(
        "- @ToAssistant(\"message\") — Send a message to the assistant (for conversational buttons like \"Tell me more\", \"Explain this\")");
    steps.add("- @OpenUrl(\"https://...\") — Navigate to a URL");
    if (bindings) {
      steps.add("- @Set($variable, value) — Set a $variable to a specific value");
      steps.add(
          "- @Reset($var1, $var2, ...) — Reset $variables to their declared defaults (e.g. @Reset($title, $priority) restores $title=\"\" and $priority=\"medium\")");
    }

    ArrayList<String> examples = new ArrayList<>();
    if (toolCalls) {
      examples.add(
          "Example — mutation + refresh + reset (PREFERRED pattern):\n"
              + "```\n"
              + "$binding = \"default\"\n"
              + "result = Mutation(\"tool_name\", {field: $binding})\n"
              + "data = Query(\"tool_name\", {}, {rows: []})\n"
              + "onSubmit = Action([@Run(result), @Run(data), @Reset($binding)])\n"
              + "```");
    }
    examples.add(
        "Example — simple nav:\n"
            + "```\n"
            + "viewBtn = Button(\"View\", Action([@OpenUrl(\"https://example.com\")]))\n"
            + "```");

    ArrayList<String> rules = new ArrayList<>();
    rules.add(
        "- Action can be assigned to a variable or inlined: Button(\"Go\", onSubmit) and Button(\"Go\", Action([...])) both work");
    if (toolCalls) {
      rules.add("- If a @Run(mutation) step fails, remaining steps are skipped (halt on failure)");
      rules.add("- @Run(queryRef) re-fetches the query (fire-and-forget, cannot fail)");
    }

    return "## Action — Button Behavior\n"
        + "\n"
        + "Action([@steps...]) wires button clicks to operations. Steps are @-prefixed built-in actions. Steps execute in order.\n"
        + "Buttons without an explicit Action prop automatically send their label to the assistant (equivalent to Action([@ToAssistant(label)])).\n"
        + "\n"
        + "Available steps:\n"
        + String.join("\n", steps)
        + "\n"
        + "\n"
        + String.join("\n\n", examples)
        + "\n"
        + "\n"
        + String.join("\n", rules);
  }

  // ─── Tools ─────────────────────────────────────────────────────────────────

  private static String renderToolsSection(List<ToolSpec> tools) {
    ArrayList<String> lines = new ArrayList<>();
    lines.add("## Available Tools");
    lines.add("");
    lines.add(
        "Use these with Query() for read operations or Mutation() for write operations. The LLM decides which is appropriate based on the tool's purpose.");
    lines.add("");
    for (ToolSpec tool : tools) {
      lines.add(renderToolSignature(tool));
    }

    ArrayList<ToolSpec> withOutput = new ArrayList<>();
    for (ToolSpec tool : tools) {
      if (hasSchema(tool.outputSchema())) withOutput.add(tool);
    }
    if (!withOutput.isEmpty()) {
      lines.add("");
      lines.add("### Default values for Query results");
      lines.add("");
      lines.add("Use these shapes as minimal Query defaults:");
      for (ToolSpec tool : withOutput) {
        lines.add("- " + tool.name() + ": `" + Json.stringify(defaultForSchema(tool.outputSchema())) + "`");
      }
    }

    lines.add("");
    lines.add(
        "CRITICAL: Use ONLY the tools listed above in Query() and Mutation() calls. Do NOT invent or guess tool names. If the user asks for functionality that doesn't match any available tool, use realistic mock data instead of fabricating a tool call.");
    return String.join("\n", lines);
  }

  private static String renderToolSignature(ToolSpec tool) {
    String args = "";
    Map<String, Object> inputSchema = tool.inputSchema();
    if (inputSchema != null) {
      Object propsObj = inputSchema.get("properties");
      if (propsObj instanceof Map<?, ?> rawProps && !rawProps.isEmpty()) {
        Map<String, Object> props = asObject(propsObj);
        List<String> required = stringList(inputSchema.get("required"));
        ArrayList<String> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
          String opt = required.contains(entry.getKey()) ? "" : "?";
          fields.add(entry.getKey() + opt + ": " + jsonSchemaTypeStr(asObject(entry.getValue())));
        }
        args = String.join(", ", fields);
      }
    }

    String returnType = "";
    if (hasSchema(tool.outputSchema())) {
      returnType = " → " + jsonSchemaTypeStr(tool.outputSchema());
    }

    String line = "- " + tool.name() + "(" + args + ")" + returnType;
    if (tool.description() != null && !tool.description().isEmpty()) {
      line += "\n  " + tool.description();
    }
    return line;
  }

  private static boolean hasSchema(Map<String, Object> schema) {
    return schema != null && !schema.isEmpty();
  }

  private static String jsonSchemaTypeStr(Map<String, Object> schema) {
    String type = schema.get("type") instanceof String s ? s : null;

    if ("string".equals(type)) {
      Object enumObj = schema.get("enum");
      if (enumObj instanceof List<?> enumVals) {
        ArrayList<String> quoted = new ArrayList<>();
        for (Object value : enumVals) quoted.add("\"" + value + "\"");
        return String.join(" | ", quoted);
      }
      return "string";
    }
    if ("number".equals(type) || "integer".equals(type)) return "number";
    if ("boolean".equals(type)) return "boolean";
    if ("array".equals(type)) {
      Object items = schema.get("items");
      if (items instanceof Map<?, ?> itemMap) return jsonSchemaTypeStr(asObject(itemMap)) + "[]";
      return "any[]";
    }
    if ("object".equals(type)) {
      Object propsObj = schema.get("properties");
      if (propsObj instanceof Map<?, ?> rawProps && !rawProps.isEmpty()) {
        Map<String, Object> props = asObject(propsObj);
        List<String> required = stringList(schema.get("required"));
        ArrayList<String> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
          String opt = required.contains(entry.getKey()) ? "" : "?";
          fields.add(entry.getKey() + opt + ": " + jsonSchemaTypeStr(asObject(entry.getValue())));
        }
        return "{" + String.join(", ", fields) + "}";
      }
      return "object";
    }
    return "any";
  }

  private static Object defaultForSchema(Map<String, Object> schema) {
    String type = schema.get("type") instanceof String s ? s : null;
    if ("string".equals(type)) return "";
    if ("number".equals(type) || "integer".equals(type)) return 0L;
    if ("boolean".equals(type)) return Boolean.FALSE;
    if ("array".equals(type)) return new ArrayList<>();
    if ("object".equals(type)) {
      Object propsObj = schema.get("properties");
      if (propsObj instanceof Map<?, ?> rawProps && !rawProps.isEmpty()) {
        Map<String, Object> props = asObject(propsObj);
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
          result.put(entry.getKey(), defaultForSchema(asObject(entry.getValue())));
        }
        return result;
      }
      return new java.util.LinkedHashMap<>();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value) {
    return (Map<String, Object>) value;
  }

  private static List<String> stringList(Object value) {
    ArrayList<String> result = new ArrayList<>();
    if (value instanceof List<?> list) {
      for (Object item : list) result.add(String.valueOf(item));
    }
    return result;
  }

  // ─── Streaming ─────────────────────────────────────────────────────────────

  private static String streamingRules(String rootName, boolean supportsExpressions) {
    ArrayList<String> steps = new ArrayList<>();
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

    return "## Hoisting & Streaming (CRITICAL)\n"
        + "\n"
        + "openui-lang supports hoisting: a reference can be used BEFORE it is defined. The parser resolves all references after the full input is parsed.\n"
        + "\n"
        + "During streaming, the output is re-parsed on every chunk. Undefined references are temporarily unresolved and appear once their definitions stream in. This creates a progressive top-down reveal — structure first, then data fills in.\n"
        + "\n"
        + "**Recommended statement order for optimal streaming:**\n"
        + String.join("\n", steps)
        + "\n"
        + "\n"
        + "Always write the root = "
        + rootName
        + "(...) statement first so the UI shell appears immediately, even before child data has streamed in.";
  }

  // ─── Important rules ───────────────────────────────────────────────────────

  private static String importantRules(String rootName, boolean toolCalls, boolean bindings) {
    ArrayList<String> verifyLines = new ArrayList<>();
    verifyLines.add("1. root = " + rootName + "(...) is the FIRST line (for optimal streaming).");
    verifyLines.add(
        "2. Every referenced name is defined. Every defined name (other than root) is reachable from root.");
    if (toolCalls) {
      verifyLines.add("3. Every Query result is referenced by at least one component.");
    }
    if (bindings) {
      verifyLines.add(
          (toolCalls ? "4" : "3") + ". Every $binding appears in at least one component or expression.");
    }

    return "## Important Rules\n"
        + "- When asked about data, generate realistic/plausible data\n"
        + "- Choose components that best represent the content (tables for comparisons, charts for trends, forms for input, etc.)\n"
        + "\n"
        + "## Final Verification\n"
        + "Before finishing, walk your output and verify:\n"
        + String.join("\n", verifyLines);
  }

  // ─── Static sections (verbatim from prompt.ts) ─────────────────────────────

  private static final String QUERY_SECTION =
      """
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

  private static final String MUTATION_SECTION =
      """
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

  private static final String INTERACTIVE_FILTERS_SECTION =
      """
      ## Interactive Filters

      To let the user filter data with a dropdown:
      1. Declare a $variable with a default: `$dateRange = "14"`
      2. Create a Select with name, items, and binding: `Select("dateRange", [SelectItem("7", "Last 7 days"), ...], null, null, $dateRange)`
      3. Wrap in FormControl for a label: `FormControl("Date Range", Select(...))`
      4. Pass $dateRange in Query args: `Query("tool", {dateRange: $dateRange}, {defaults})`
      5. When the user changes the Select, $dateRange updates and the Query automatically re-fetches

      FILTER WIRING RULE: If a $binding filter is visible in the UI, EVERY relevant Query MUST reference that $binding in its args. Never show a filter dropdown while hardcoding the query args.

      Rules for $variables:
      - $variables hold simple values (strings or numbers), NOT arrays or objects
      - $variables must be bound to a Select/Input component via the value argument (last positional arg) to be interactive
      - Queries must use regular identifiers (NOT $variables): `metrics = Query(...)` not `$metrics = Query(...)`
      - **Auto-declare**: You do NOT need to explicitly declare $variables. If you use `$foo` without declaring it, the parser auto-creates `$foo = null`. You can still declare explicitly to set a default: `$days = "14"`

      ## Forms

      Simple form — no $bindings needed. Field values are managed internally by the Form via the name prop:
      ```
      contactForm = Form("contact", submitBtn, [nameField, emailField])
      nameField = FormControl("Name", Input("name", "Your name", "text", {required: true}))
      emailField = FormControl("Email", Input("email", "your@email.com", "email", {required: true, email: true}))
      submitBtn = Button("Submit")
      ```

      Use $bindings when you need to read field values elsewhere (in Action context, Query args, or conditionals). They are auto-declared:
      ```
      $role = "engineer"
      contactForm = Form("contact", submitBtn, [nameField, emailField, roleField])
      nameField = FormControl("Name", Input("name", "Enter your name", "text", {required: true}, $name))
      emailField = FormControl("Email", Input("email", "Enter your email", "email", {required: true, email: true}, $email))
      roleField = FormControl("Role", Select("role", [SelectItem("engineer", "Engineer"), SelectItem("designer", "Designer"), SelectItem("pm", "PM")], null, {required: true}, $role))
      submitBtn = Button("Submit")
      ```

      For form + mutation patterns (create, refresh, reset), see the Action section example above.

      IMPORTANT: Always add validation rules to form fields used with Mutations. Use OBJECT syntax: {required: true, email: true, minLength: 8}. The renderer shows error messages automatically and blocks submit when validation fails.""";

  private static final String TOOL_WORKFLOW_SECTION =
      """
      ## Data Workflow

      When tools are available, follow this workflow:
      1. FIRST: Call the most relevant tool to inspect the real data shape before generating code
      2. Use Query() for READ operations (data that should stay live) — NEVER hardcode tool results as literal arrays or objects
      3. Use Mutation() for WRITE operations (create, update, delete) — triggered by button clicks via Action([@Run(mutationRef)])
      4. Use the real data from step 1 as condensed Query defaults (3-5 rows) so the UI renders immediately
      5. Use @-prefixed builtins (@Count, @Filter, @Sort, @Sum) on Query results for KPIs and aggregations — the runtime evaluates these live on every refresh
      6. Hardcoded arrays are ONLY for static display data (labels, options) where no tool exists

      WRONG — you called a tool and got data back, but you inlined the results:
      ```
      openCount = 2
      item1 = SomeComp("first item title")
      item2 = SomeComp("second item title")
      list = Stack([item1, item2])
      chart = SomeChart(["A", "B"], [12, 8])
      ```
      This is static — it shows stale data and won't update. Creating item1, item2, item3... manually is ALWAYS wrong when a tool exists.

      RIGHT — use Query() for live data, Mutation() for writes, @builtins to derive values:
      ```
      data = Query("tool_name", {}, {rows: []})
      openCount = @Count(@Filter(data.rows, "field", "==", "value"))
      list = @Each(data.rows, "item", SomeComp(item.title, item.field))
      createResult = Mutation("create_tool", {title: $title})
      submitBtn = Button("Create", Action([@Run(createResult), @Run(data), @Reset($title)]))
      ```
      Everything derives from the Query — when data refreshes, the entire dashboard updates automatically.""";

  private static final String EDIT_MODE_SECTION =
      """
      ## Edit Mode

      The runtime merges by statement name: same name = replace, new name = append.
      Output ONLY statements that changed or are new. Everything else is kept automatically.

      ### Delete
      To remove a component, update the parent to exclude it from its children array. Orphaned statements are automatically garbage-collected.
      Example — remove chart: `root = Stack([header, kpiRow, table])` — chart is no longer in the children list, so it and any statements only it referenced are auto-deleted.

      ### Patch size guide
      - Changing a title or label: 1 statement
      - Adding a component: 2-3 statements (the new component + parent update)
      - Removing a component: 1 statement (re-declare parent without the removed child)
      - Adding a filter + wiring to query: 3-5 statements
      - Restructuring into tabs: 5-10 statements

      ### Rules
      - Reuse existing statement names exactly — do not rename
      - Do NOT re-emit unchanged statements — the runtime keeps them
      - A typical edit patch is 1-10 statements, not 20+
      - If the existing code already satisfies the request, output only the root statement
      - NEVER output the entire program as a patch. Only output what actually changes
      - If you are about to output more than 10 statements, reconsider — most edits need fewer""";

  private static final String INLINE_MODE_SECTION =
      """
      ## Inline Mode

      You are in inline mode. You can respond in two ways:

      ### 1. Code response (when the user wants to CREATE or CHANGE the UI)
      Wrap openui-lang code in triple-backtick fences. You can include explanatory text before/after:

      Here's your dashboard:

      ```openui-lang
      root = RootComp([header, content])
      header = SomeHeader("Title")
      content = SomeContent("Hello world")
      ```

      I created a simple layout with a header.

      ### 2. Text-only response (when the user asks a QUESTION)
      If the user asks "what is this?", "explain the chart", "how does this work", etc. — respond with plain text. Do NOT output any openui-lang code. The existing dashboard stays unchanged.

      ### Rules
      - When the user asks for changes, output ONLY the changed/new statements in a fenced block
      - When the user asks a question, respond with text only — NO code. The dashboard stays unchanged.
      - The parser extracts code from fences automatically. Text outside fences is shown as chat.""";

  record PromptInput(
      String preamble,
      String root,
      Map<String, ComponentPromptSpec> components,
      List<ComponentGroup> componentGroups,
      DataModelSpec dataModel,
      List<ToolSpec> tools,
      List<String> examples,
      List<String> additionalRules,
      Boolean editMode,
      Boolean inlineMode,
      Boolean toolCalls,
      Boolean bindings) {}
}
