package dev.openui.langcore.library;

/**
 * Static factory for starting component definitions via the fluent builder API.
 *
 * <pre>{@code
 * ComponentDef card = Libraries.defineComponent("Card")
 *     .description("A card component")
 *     .prop("title").type("string").required().add()
 *     .prop("subtitle").type("string").optional().add()
 *     .build();
 * }</pre>
 *
 * Ref: Design §13
 */
public final class Libraries {

    private Libraries() {}

    /** Start building a component definition with the given name. */
    public static ComponentDefBuilder defineComponent(String name) {
        return new ComponentDefBuilder(name);
    }
}
