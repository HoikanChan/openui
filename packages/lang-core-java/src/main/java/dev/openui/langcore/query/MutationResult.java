package dev.openui.langcore.query;

/**
 * Current state of a single mutation, returned by
 * {@link QueryManager#getMutationResult(String)}.
 *
 * <p>Fields mirror the TypeScript {@code MutationResult} interface in {@code queryManager.ts}.
 *
 * @param status one of {@code "idle"}, {@code "loading"}, {@code "success"}, {@code "error"}
 * @param data   the result value on success (may be {@code null})
 * @param error  the error value on failure (may be {@code null})
 *
 * Ref: Design §15
 */
public record MutationResult(String status, Object data, Object error) {

    /** Convenience factory for the initial idle state. */
    public static MutationResult idle() {
        return new MutationResult("idle", null, null);
    }
}
