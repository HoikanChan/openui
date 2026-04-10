package dev.openui.langcore;

/**
 * Main façade for the OpenUI Lang core library.
 * Provides factory methods for parsers, the streaming parser, merge utilities,
 * and library/query manager creation.
 */
public final class LangCore {

    private LangCore() {}

    /**
     * Create a one-shot parser bound to the given JSON Schema string.
     * TODO: implement
     */
    public static Object createParser(String jsonSchema) {
        throw new UnsupportedOperationException("TODO: implement createParser");
    }

    /**
     * Create a streaming parser bound to the given JSON Schema string.
     * TODO: implement
     */
    public static Object createStreamingParser(String jsonSchema) {
        throw new UnsupportedOperationException("TODO: implement createStreamingParser");
    }

    /**
     * Parse an OpenUI Lang program in one shot.
     * TODO: implement
     */
    public static Object parse(String input, String jsonSchema) {
        throw new UnsupportedOperationException("TODO: implement parse");
    }

    /**
     * Merge a patch program into an existing statement map.
     * TODO: implement
     */
    public static Object mergeStatements(Object existing, String patchText, String jsonSchema) {
        throw new UnsupportedOperationException("TODO: implement mergeStatements");
    }

    /**
     * Create a library from the given definition object.
     * TODO: implement
     */
    public static Object createLibrary(Object definition) {
        throw new UnsupportedOperationException("TODO: implement createLibrary");
    }
}
