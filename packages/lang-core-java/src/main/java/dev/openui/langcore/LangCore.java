package dev.openui.langcore;

import dev.openui.langcore.merge.Merger;
import dev.openui.langcore.parser.OneShot;
import dev.openui.langcore.parser.result.ParseResult;
import dev.openui.langcore.parser.SchemaRegistry;
import dev.openui.langcore.parser.StreamParser;
import dev.openui.langcore.parser.stmt.Statement;

import java.util.Map;

/**
 * Main façade for the OpenUI Lang core library.
 * Provides factory methods for parsers, the streaming parser, merge utilities,
 * and library/query manager creation.
 */
public final class LangCore {

    private LangCore() {}

    /**
     * Create a one-shot parser bound to the given JSON Schema string.
     */
    public static OneShot createParser(String jsonSchema) {
        return new OneShot(SchemaRegistry.fromJson(jsonSchema));
    }

    /**
     * Create a streaming parser bound to the given JSON Schema string.
     * Ref: Req 9 AC1
     */
    public static StreamParser createStreamingParser(String jsonSchema) {
        return new StreamParser(SchemaRegistry.fromJson(jsonSchema));
    }

    /**
     * Parse an OpenUI Lang program in one shot.
     */
    public static ParseResult parse(String input, String jsonSchema) {
        return createParser(jsonSchema).parse(input);
    }

    /**
     * Merge a patch program into an existing statement map.
     * Ref: Req 12 AC1-8
     */
    public static Map<String, Statement> mergeStatements(
            Map<String, Statement> existing, String patchText, String jsonSchema) {
        return Merger.mergeStatements(existing, patchText, SchemaRegistry.fromJson(jsonSchema));
    }

    /**
     * Create a library from the given definition object.
     * TODO: implement
     */
    public static Object createLibrary(Object definition) {
        throw new UnsupportedOperationException("TODO: implement createLibrary");
    }
}
