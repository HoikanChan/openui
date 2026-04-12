package dev.openui.langcore.query;

import dev.openui.langcore.parser.result.OpenUIError;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all query results, produced by
 * {@link QueryManager#getSnapshot()}.
 *
 * <p>Fields mirror the TypeScript {@code QuerySnapshot} interface in {@code queryManager.ts}.
 *
 * @param data       map of {@code statementId} → resolved result (or default value)
 * @param loading    statement IDs currently in their first (non-cached) fetch
 * @param refetching statement IDs currently re-fetching with a cached value still present
 * @param errors     any runtime errors that occurred during the last fetch cycle
 *
 * Ref: Design §15
 */
public record QuerySnapshot(
        Map<String, Object> data,
        List<String>        loading,
        List<String>        refetching,
        List<OpenUIError>   errors
) {

    /** Empty snapshot with no data, no loading, and no errors. */
    public static QuerySnapshot empty() {
        return new QuerySnapshot(Map.of(), List.of(), List.of(), List.of());
    }
}
