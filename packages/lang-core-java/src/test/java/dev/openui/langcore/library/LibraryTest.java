package dev.openui.langcore.library;

import dev.openui.langcore.parser.ComponentSchema;
import dev.openui.langcore.parser.SchemaRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Library fluent builder API.
 *
 * Covers: builder round-trip, toJSONSchema, prompt(), unknown root throws,
 * and SchemaRegistry.fromLibrary lookup.
 *
 * Ref: Design §13, Task 17.5
 */
class LibraryTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static LibraryDefinition sampleDefinition() {
        ComponentDef button = Libraries.defineComponent("Button")
                .description("A clickable button")
                .prop("label").type("string").required().add()
                .prop("disabled").type("boolean").optional().defaultValue(false).add()
                .build();

        ComponentDef card = Libraries.defineComponent("Card")
                .description("A card container")
                .prop("title").type("string").required().add()
                .prop("items").type("Button[]").optional().add()
                .build();

        return new LibraryDefinition(
                Map.of("Button", button, "Card", card),
                List.of(new ComponentGroup("Core", List.of("Button", "Card"), List.of())),
                "Card"
        );
    }

    // -------------------------------------------------------------------------
    // Builder round-trip
    // -------------------------------------------------------------------------

    @Test
    void builder_roundTrip_componentDef() {
        ComponentDef button = Libraries.defineComponent("Button")
                .description("A button")
                .prop("label").type("string").required().add()
                .prop("count").type("number").optional().defaultValue(0).add()
                .build();

        assertEquals("Button", button.name());
        assertEquals("A button", button.description());
        assertEquals(2, button.props().size());

        PropDef label = button.props().get(0);
        assertEquals("label", label.name());
        assertEquals("string", label.typeAnnotation());
        assertTrue(label.required());
        assertFalse(label.isArray());
        assertFalse(label.isReactive());

        PropDef count = button.props().get(1);
        assertEquals("count", count.name());
        assertFalse(count.required());
        assertEquals(0, count.defaultValue());
    }

    @Test
    void builder_arrayType_setsIsArray() {
        ComponentDef c = Libraries.defineComponent("List")
                .prop("items").type("string[]").add()
                .build();

        PropDef items = c.props().get(0);
        assertTrue(items.isArray());
        assertEquals("string[]", items.typeAnnotation());
    }

    @Test
    void builder_reactiveType_setsIsReactive() {
        ComponentDef c = Libraries.defineComponent("Input")
                .prop("value").type("$binding<string>").add()
                .build();

        PropDef value = c.props().get(0);
        assertTrue(value.isReactive());
    }

    // -------------------------------------------------------------------------
    // createLibrary validates root
    // -------------------------------------------------------------------------

    @Test
    void createLibrary_validRoot_succeeds() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        assertEquals("Card", lib.root());
        assertEquals(2, lib.components().size());
        assertEquals(1, lib.componentGroups().size());
    }

    @Test
    void createLibrary_unknownRoot_throws() {
        LibraryDefinition bad = new LibraryDefinition(
                Map.of("Button", Libraries.defineComponent("Button").build()),
                List.of(),
                "NonExistent"
        );
        assertThrows(IllegalArgumentException.class, () -> Libraries.createLibrary(bad));
    }

    @Test
    void createLibrary_nullRoot_succeeds() {
        LibraryDefinition def = new LibraryDefinition(
                Map.of("Button", Libraries.defineComponent("Button").build()),
                List.of(),
                null
        );
        Library lib = Libraries.createLibrary(def);
        assertNull(lib.root());
    }

    // -------------------------------------------------------------------------
    // toJSONSchema
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void toJSONSchema_containsDefs() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        Map<String, Object> schema = lib.toJSONSchema();

        assertTrue(schema.containsKey("$defs"), "schema must have $defs");
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        assertTrue(defs.containsKey("Button"));
        assertTrue(defs.containsKey("Card"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toJSONSchema_buttonPropertiesAndRequired() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        Map<String, Object> schema = lib.toJSONSchema();
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        Map<String, Object> buttonSchema = (Map<String, Object>) defs.get("Button");

        Map<String, Object> props = (Map<String, Object>) buttonSchema.get("properties");
        assertTrue(props.containsKey("label"));
        assertTrue(props.containsKey("disabled"));

        List<String> required = (List<String>) buttonSchema.get("required");
        assertNotNull(required);
        assertTrue(required.contains("label"));
        assertFalse(required.contains("disabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toJSONSchema_arrayPropHasItemsSchema() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        Map<String, Object> schema = lib.toJSONSchema();
        Map<String, Object> defs   = (Map<String, Object>) schema.get("$defs");
        Map<String, Object> card   = (Map<String, Object>) defs.get("Card");
        Map<String, Object> props  = (Map<String, Object>) card.get("properties");
        Map<String, Object> items  = (Map<String, Object>) props.get("items");

        assertEquals("array", items.get("type"));
        assertNotNull(items.get("items"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toJSONSchema_rootRefPresent() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        Map<String, Object> schema = lib.toJSONSchema();
        assertEquals("#/$defs/Card", schema.get("$ref"));
    }

    // -------------------------------------------------------------------------
    // prompt()
    // -------------------------------------------------------------------------

    @Test
    void prompt_includesComponentSignatures() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        String prompt = lib.prompt(PromptOptions.empty());

        assertTrue(prompt.contains("Button"), "prompt must mention Button");
        assertTrue(prompt.contains("Card"),   "prompt must mention Card");
        // label is required so no '?'; disabled is optional so '?'
        assertTrue(prompt.contains("label: string"),     "prompt must include label prop");
        assertTrue(prompt.contains("disabled?: boolean"), "prompt must include disabled? prop");
    }

    // -------------------------------------------------------------------------
    // SchemaRegistry.fromLibrary
    // -------------------------------------------------------------------------

    @Test
    void fromLibrary_lookup_returnsCorrectSchema() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        SchemaRegistry registry = SchemaRegistry.fromLibrary(lib);

        Optional<ComponentSchema> buttonSchema = registry.lookup("Button");
        assertTrue(buttonSchema.isPresent());

        ComponentSchema cs = buttonSchema.get();
        assertEquals(2, cs.props().size());
        assertEquals("label",    cs.props().get(0).name());
        assertTrue(cs.props().get(0).required());
        assertEquals("disabled", cs.props().get(1).name());
        assertFalse(cs.props().get(1).required());
        assertEquals(false, cs.props().get(1).defaultValue());
    }

    @Test
    void fromLibrary_unknownComponent_returnsEmpty() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        SchemaRegistry registry = SchemaRegistry.fromLibrary(lib);
        assertTrue(registry.lookup("NonExistent").isEmpty());
    }

    @Test
    void fromLibrary_preservesPropOrder() {
        Library lib = Libraries.createLibrary(sampleDefinition());
        SchemaRegistry registry = SchemaRegistry.fromLibrary(lib);
        ComponentSchema cs = registry.lookup("Button").orElseThrow();
        assertEquals("label",    cs.props().get(0).name());
        assertEquals("disabled", cs.props().get(1).name());
    }
}
