package dev.openui.langcore.parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SchemaRegistry} — round-trip parsing of JSON Schema
 * subsets relevant to OpenUI components.
 *
 * Ref: Req 4 AC2-4
 */
class SchemaRegistryTest {

    // Shared schema used by most tests
    private static final String BUTTON_CARD_SCHEMA = """
            {
              "$defs": {
                "Button": {
                  "properties": {
                    "label":    { "type": "string",  "default": "Click me" },
                    "disabled": { "type": "boolean" }
                  },
                  "required": ["label"]
                },
                "Card": {
                  "properties": {
                    "title": { "type": "string" },
                    "body":  { "type": "string" }
                  },
                  "required": ["title", "body"]
                }
              }
            }
            """;

    // -------------------------------------------------------------------------
    // AC2 – unknown component lookup returns empty
    // -------------------------------------------------------------------------

    @Test
    void unknownComponent_returnsEmpty() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        assertEquals(Optional.empty(), reg.lookup("Unknown"));
    }

    // -------------------------------------------------------------------------
    // Null / blank input → empty registry
    // -------------------------------------------------------------------------

    @Test
    void fromJson_null_returnsEmpty() {
        SchemaRegistry reg = SchemaRegistry.fromJson(null);
        assertEquals(Optional.empty(), reg.lookup("Button"));
    }

    @Test
    void fromJson_blank_returnsEmpty() {
        SchemaRegistry reg = SchemaRegistry.fromJson("   ");
        assertEquals(Optional.empty(), reg.lookup("Button"));
    }

    @Test
    void fromJson_emptyString_returnsEmpty() {
        SchemaRegistry reg = SchemaRegistry.fromJson("");
        assertEquals(Optional.empty(), reg.lookup("Button"));
    }

    // -------------------------------------------------------------------------
    // AC4 – required props
    // -------------------------------------------------------------------------

    @Test
    void requiredProp_isMarkedRequired() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema button = reg.lookup("Button").orElseThrow();
        PropDef label = button.props().get(0);
        assertEquals("label", label.name());
        assertTrue(label.required(), "label should be required");
    }

    @Test
    void optionalProp_isNotRequired() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema button = reg.lookup("Button").orElseThrow();
        PropDef disabled = button.props().get(1);
        assertEquals("disabled", disabled.name());
        assertFalse(disabled.required(), "disabled should be optional");
    }

    // -------------------------------------------------------------------------
    // AC4 – default values
    // -------------------------------------------------------------------------

    @Test
    void defaultStringValue_isPreserved() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema button = reg.lookup("Button").orElseThrow();
        PropDef label = button.props().get(0);
        assertEquals("Click me", label.defaultValue());
    }

    @Test
    void noDefault_defaultValueIsNull() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema button = reg.lookup("Button").orElseThrow();
        PropDef disabled = button.props().get(1);
        assertNull(disabled.defaultValue(), "disabled has no default — should be null");
    }

    // -------------------------------------------------------------------------
    // AC2 – props order preserved (key order in properties)
    // -------------------------------------------------------------------------

    @Test
    void propsOrder_preservedFromJsonKeyOrder() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema button = reg.lookup("Button").orElseThrow();
        List<String> names = button.props().stream().map(PropDef::name).toList();
        assertEquals(List.of("label", "disabled"), names);
    }

    @Test
    void cardProps_allRequiredAndOrdered() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        ComponentSchema card = reg.lookup("Card").orElseThrow();
        List<String> names = card.props().stream().map(PropDef::name).toList();
        assertEquals(List.of("title", "body"), names);
        card.props().forEach(p -> assertTrue(p.required(), p.name() + " should be required"));
    }

    // -------------------------------------------------------------------------
    // Multiple components in $defs
    // -------------------------------------------------------------------------

    @Test
    void multipleComponents_allLookupSucceed() {
        SchemaRegistry reg = SchemaRegistry.fromJson(BUTTON_CARD_SCHEMA);
        assertTrue(reg.lookup("Button").isPresent());
        assertTrue(reg.lookup("Card").isPresent());
    }

    // -------------------------------------------------------------------------
    // Default values: number and boolean
    // -------------------------------------------------------------------------

    @Test
    void defaultNumberValue_isPreserved() {
        String schema = """
                {
                  "$defs": {
                    "Slider": {
                      "properties": {
                        "value": { "type": "number", "default": 42 }
                      },
                      "required": []
                    }
                  }
                }
                """;
        SchemaRegistry reg = SchemaRegistry.fromJson(schema);
        ComponentSchema slider = reg.lookup("Slider").orElseThrow();
        assertEquals(42.0, (Double) slider.props().get(0).defaultValue(), 0.001);
    }

    @Test
    void defaultBooleanValue_isPreserved() {
        String schema = """
                {
                  "$defs": {
                    "Toggle": {
                      "properties": {
                        "on": { "type": "boolean", "default": true }
                      },
                      "required": []
                    }
                  }
                }
                """;
        SchemaRegistry reg = SchemaRegistry.fromJson(schema);
        ComponentSchema toggle = reg.lookup("Toggle").orElseThrow();
        assertEquals(Boolean.TRUE, toggle.props().get(0).defaultValue());
    }

    // -------------------------------------------------------------------------
    // static empty()
    // -------------------------------------------------------------------------

    @Test
    void staticEmpty_lookupReturnsEmpty() {
        SchemaRegistry reg = SchemaRegistry.empty();
        assertEquals(Optional.empty(), reg.lookup("Button"));
        assertEquals(Optional.empty(), reg.lookup("Anything"));
    }
}
