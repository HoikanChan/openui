package dev.openui.langcore.runtime;

import java.util.List;

/**
 * Eager built-in functions for {@code @Name(...)} expressions.
 *
 * <p>All methods receive already-evaluated (resolved) arguments.
 * Implementations are provided in Task 13.1.
 *
 * Ref: Req 10 AC1-11
 */
public final class Builtins {

    private Builtins() {}

    /** @Count(array) — array length or 0. Ref: Req 10 AC1 */
    public static Object count(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @First(array) — first element or null. Ref: Req 10 AC2 */
    public static Object first(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Last(array) — last element or null. Ref: Req 10 AC3 */
    public static Object last(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Sum(array) — sum of elements. Ref: Req 10 AC4 */
    public static Object sum(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Avg(array) — numeric average. Ref: Req 10 AC5 */
    public static Object avg(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Min(array) — numeric minimum. Ref: Req 10 AC6 */
    public static Object min(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Max(array) — numeric maximum. Ref: Req 10 AC6 */
    public static Object max(Object arr) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Sort(array, field, direction?) — sorted copy. Ref: Req 10 AC7 */
    public static Object sort(Object arr, Object field, Object dir) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Filter(array, field, operator, value) — filtered copy. Ref: Req 10 AC8 */
    public static Object filter(Object arr, Object field, Object op, Object val) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Round(number, decimals?) — rounded value. Ref: Req 10 AC9 */
    public static Object round(Object n, Object dec) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Abs(number) — absolute value. Ref: Req 10 AC10 */
    public static Object abs(Object n) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Floor(number) — floor. Ref: Req 10 AC11 */
    public static Object floor(Object n) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }

    /** @Ceil(number) — ceiling. Ref: Req 10 AC11 */
    public static Object ceil(Object n) {
        throw new UnsupportedOperationException("TODO: Task 13.1");
    }
}
