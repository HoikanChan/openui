/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Best-effort, allocation-free-as-possible parser that recognizes ISO-8601 date/datetime strings
 * and numeric epoch timestamps for the profiler's time-column detection.
 *
 * <p>Recognized string forms: {@code yyyy-MM-dd} (date-only), {@code yyyy-MM-ddTHH:mm[:ss[.SSS]]}
 * with optional zone offset, and {@code yyyy-MM-dd HH:mm[:ss[.SSS]]} with optional zone offset.
 * Recognized numeric forms: epoch seconds in {@code [1_000_000_000, 9_999_999_999]} and epoch
 * milliseconds in {@code [1_000_000_000_000, 9_999_999_999_999]}. Values outside those windows are
 * not treated as timestamps, so ordinary measures never misfire.
 *
 * @since 2026
 */
final class TimestampParser {
    /** Inclusive lower bound (in seconds) for an epoch-seconds timestamp (~2001). */
    private static final long EPOCH_SECONDS_MIN = 1_000_000_000L;

    /** Inclusive upper bound (in seconds) for an epoch-seconds timestamp (~2286). */
    private static final long EPOCH_SECONDS_MAX = 9_999_999_999L;

    /** Inclusive lower bound (in milliseconds) for an epoch-millis timestamp. */
    private static final long EPOCH_MILLIS_MIN = 1_000_000_000_000L;

    /** Inclusive upper bound (in milliseconds) for an epoch-millis timestamp. */
    private static final long EPOCH_MILLIS_MAX = 9_999_999_999_999L;

    /** Milliseconds in one second, used to widen epoch seconds to millis. */
    private static final long MILLIS_PER_SECOND = 1_000L;

    /** Minimum length of a date-like string ({@code yyyy-MM-dd}). */
    private static final int MIN_DATE_TEXT_LENGTH = 10;

    /** Length of the {@code yyyy-MM-dd} prefix inside a datetime string. */
    private static final int DATE_PREFIX_LENGTH = 10;

    /** Index of the year/month separator in a date string ({@code yyyy-MM-dd}). */
    private static final int YEAR_SEPARATOR_INDEX = 4;

    /** Index of the month/day separator in a date string ({@code yyyy-MM-dd}). */
    private static final int MONTH_SEPARATOR_INDEX = 7;

    private TimestampParser() {}

    /**
     * Reports whether a normalized column name indicates time semantics.
     *
     * @param normalizedName the lowercased column name with underscores and hyphens stripped
     * @return {@code true} when the name contains a time keyword or ends with {@code at}
     */
    static boolean isTimeName(String normalizedName) {
        return normalizedName.contains("time")
                || normalizedName.contains("date")
                || normalizedName.contains("timestamp")
                || normalizedName.contains("ts")
                || normalizedName.endsWith("at");
    }

    /**
     * Parses a string value into epoch milliseconds when it looks like an ISO-8601 date/datetime.
     *
     * @param text the raw string value
     * @return the parsed {@link ParsedTimestamp}, or {@code null} when the text is not date-like
     */
    static ParsedTimestamp parseText(String text) {
        if (text == null || text.length() < MIN_DATE_TEXT_LENGTH) {
            return null;
        }
        char yearSep = text.charAt(YEAR_SEPARATOR_INDEX);
        char monthSep = text.charAt(MONTH_SEPARATOR_INDEX);
        if (yearSep != '-' || monthSep != '-') {
            return null;
        }
        try {
            if (text.length() == MIN_DATE_TEXT_LENGTH) {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return new ParsedTimestamp(
                        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true);
            }
            return parseDateTimeText(text);
        } catch (DateTimeParseException | ArithmeticException error) {
            return null;
        }
    }

    /**
     * Parses a numeric value into epoch milliseconds when it falls in a plausible epoch window.
     *
     * @param value the raw numeric value
     * @return the parsed {@link ParsedTimestamp}, or {@code null} outside the epoch windows
     */
    static ParsedTimestamp parseNumber(Number value) {
        long raw = value.longValue();
        if (raw >= EPOCH_MILLIS_MIN && raw <= EPOCH_MILLIS_MAX) {
            return new ParsedTimestamp(raw, false);
        }
        if (raw >= EPOCH_SECONDS_MIN && raw <= EPOCH_SECONDS_MAX) {
            return new ParsedTimestamp(raw * MILLIS_PER_SECOND, false);
        }
        return null;
    }

    /**
     * Parses a datetime string (with either a {@code T} or space separator) into epoch millis.
     *
     * @param text the raw string value, already known to start with a date prefix
     * @return the parsed {@link ParsedTimestamp}, or {@code null} when the text is not date-like
     */
    private static ParsedTimestamp parseDateTimeText(String text) {
        char separator = text.charAt(DATE_PREFIX_LENGTH);
        if (separator != 'T' && separator != 't' && separator != ' ') {
            return null;
        }
        String normalized = separator == ' ' ? replaceSeparator(text) : text;
        try {
            if (hasZoneSuffix(normalized)) {
                OffsetDateTime zoned = OffsetDateTime.parse(normalized);
                return new ParsedTimestamp(zoned.toInstant().toEpochMilli(), false);
            }
            LocalDateTime local = LocalDateTime.parse(normalized);
            return new ParsedTimestamp(local.toInstant(ZoneOffset.UTC).toEpochMilli(), false);
        } catch (DateTimeParseException | ArithmeticException error) {
            return null;
        }
    }

    /**
     * Replaces the space separator at the date/time boundary with the ISO {@code T} separator.
     *
     * @param text the raw datetime string using a space separator
     * @return the normalized string with a {@code T} separator
     */
    private static String replaceSeparator(String text) {
        return text.substring(0, DATE_PREFIX_LENGTH) + 'T' + text.substring(DATE_PREFIX_LENGTH + 1);
    }

    /**
     * Reports whether a normalized datetime string carries an explicit zone suffix.
     *
     * @param text the normalized datetime string (with a {@code T} separator)
     * @return {@code true} when the string ends with {@code Z} or a {@code ±HH:mm} offset
     */
    private static boolean hasZoneSuffix(String text) {
        String tail = text.substring(DATE_PREFIX_LENGTH + 1);
        if (tail.endsWith("Z") || tail.endsWith("z")) {
            return true;
        }
        int plus = tail.indexOf('+');
        int minus = tail.lastIndexOf('-');
        return plus > 0 || minus > 0;
    }

    /**
     * Normalizes a column name for keyword matching: lowercased, underscores and hyphens stripped.
     *
     * @param name the raw column name
     * @return the normalized name
     */
    static String normalizeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char c = lower.charAt(index);
            if (c != '_' && c != '-') {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * One successfully parsed timestamp value.
     *
     * @param epochMs the instant in epoch milliseconds (UTC)
     * @param dateOnly whether the source text was a date-only value with no time-of-day component
     * @since 2026
     */
    record ParsedTimestamp(long epochMs, boolean dateOnly) {}
}
