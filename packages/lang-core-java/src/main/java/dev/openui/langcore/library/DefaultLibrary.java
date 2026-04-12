package dev.openui.langcore.library;

import dev.openui.langcore.prompt.ComponentPromptSpec;
import dev.openui.langcore.prompt.PromptGenerator;
import dev.openui.langcore.prompt.PromptSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link Library}.
 *
 * Ref: Design §13
 */
final class DefaultLibrary implements Library {

    private final Map<String, ComponentDef> components;
    private final List<ComponentGroup>      componentGroups;
    private final String                    root;

    DefaultLibrary(LibraryDefinition def) {
        if (def.root() != null && !def.components().containsKey(def.root())) {
            throw new IllegalArgumentException(
                    "Root component \"" + def.root() + "\" not found in components map");
        }
        this.components      = Map.copyOf(def.components());
        this.componentGroups = def.componentGroups() != null
                               ? List.copyOf(def.componentGroups())
                               : List.of();
        this.root            = def.root();
    }

    @Override
    public Map<String, ComponentDef> components() { return components; }

    @Override
    public List<ComponentGroup> componentGroups() { return componentGroups; }

    @Override
    public String root() { return root; }

    // -------------------------------------------------------------------------
    // prompt()
    // -------------------------------------------------------------------------

    @Override
    public String prompt(PromptOptions options) {
        Map<String, ComponentPromptSpec> promptComponents = new LinkedHashMap<>();
        for (Map.Entry<String, ComponentDef> e : components.entrySet()) {
            ComponentDef cd = e.getValue();
            promptComponents.put(e.getKey(),
                    new ComponentPromptSpec(buildSignature(cd), cd.description()));
        }

        // Convert library ComponentGroups → prompt ComponentGroups
        List<dev.openui.langcore.prompt.ComponentGroup> groups = new ArrayList<>();
        for (ComponentGroup g : componentGroups) {
            groups.add(new dev.openui.langcore.prompt.ComponentGroup(
                    g.name(), g.components(), g.notes()));
        }

        boolean hasTools = options.tools() != null && !options.tools().isEmpty();
        boolean toolCalls = options.toolCalls() || hasTools;
        boolean bindings  = options.bindings()  || toolCalls;

        PromptSpec spec = new PromptSpec(
                root,
                promptComponents,
                groups,
                options.tools()           != null ? options.tools()           : List.of(),
                options.editMode(),
                options.inlineMode(),
                toolCalls,
                bindings,
                options.preamble(),
                options.examples()        != null ? options.examples()        : List.of(),
                options.toolExamples()    != null ? options.toolExamples()    : List.of(),
                options.additionalRules() != null ? options.additionalRules() : List.of()
        );

        return PromptGenerator.generatePrompt(spec);
    }

    /** Build a component signature string like {@code "Button(label: string, onClick?: Action)"}. */
    private static String buildSignature(ComponentDef cd) {
        StringBuilder sb = new StringBuilder(cd.name()).append('(');
        List<PropDef> props = cd.props();
        for (int i = 0; i < props.size(); i++) {
            if (i > 0) sb.append(", ");
            PropDef p = props.get(i);
            sb.append(p.name());
            if (!p.required()) sb.append('?');
            sb.append(": ").append(p.typeAnnotation() != null ? p.typeAnnotation() : "any");
        }
        sb.append(')');
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // toJSONSchema()
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> toJSONSchema() {
        Map<String, Object> defs = new LinkedHashMap<>();
        for (Map.Entry<String, ComponentDef> e : components.entrySet()) {
            defs.put(e.getKey(), buildComponentSchema(e.getValue()));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$defs", defs);
        if (root != null) {
            schema.put("$ref", "#/$defs/" + root);
        }
        return schema;
    }

    private static Map<String, Object> buildComponentSchema(ComponentDef cd) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String>        required   = new ArrayList<>();

        for (PropDef p : cd.props()) {
            properties.put(p.name(), propToJsonSchema(p));
            if (p.required()) {
                required.add(p.name());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        if (cd.description() != null) {
            schema.put("description", cd.description());
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> propToJsonSchema(PropDef p) {
        String annotation = p.typeAnnotation() != null ? p.typeAnnotation() : "any";

        if (p.isArray()) {
            // e.g. "string[]" → items type "string"
            String itemType = annotation.endsWith("[]")
                              ? annotation.substring(0, annotation.length() - 2)
                              : "object";
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", toJsonSchemaType(itemType));
            return schema;
        }

        if (p.isReactive()) {
            // $binding<T> — accept any value
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("description", "reactive binding");
            return schema;
        }

        return toJsonSchemaType(annotation);
    }

    private static Map<String, Object> toJsonSchemaType(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (type) {
            case "string"  -> schema.put("type", "string");
            case "number"  -> schema.put("type", "number");
            case "boolean" -> schema.put("type", "boolean");
            case "any"     -> { /* no type constraint */ }
            default -> {
                // treat as a reference to another component def
                schema.put("$ref", "#/$defs/" + type);
            }
        }
        return schema;
    }
}
