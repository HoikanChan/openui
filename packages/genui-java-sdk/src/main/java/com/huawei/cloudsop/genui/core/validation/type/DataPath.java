package com.huawei.cloudsop.genui.core.validation.type;

import java.util.ArrayList;
import java.util.List;

/** A concrete {@code data...} path containing only literal keys and indexes. */
public record DataPath(List<Segment> segments, String source) {

    public DataPath {
        segments = List.copyOf(segments);
    }

    public static DataPath parse(String source) {
        if (source == null || !(source.equals("data") || source.startsWith("data.") || source.startsWith("data["))) {
            throw new IllegalArgumentException("Data path must start with data: " + source);
        }
        List<Segment> segments = new ArrayList<>();
        int index = 4;
        while (index < source.length()) {
            char marker = source.charAt(index);
            if (marker == '.') {
                int start = ++index;
                while (index < source.length() && isIdentifierPart(source.charAt(index))) {
                    index++;
                }
                if (start == index) {
                    throw new IllegalArgumentException("Empty member in data path: " + source);
                }
                segments.add(new Key(source.substring(start, index)));
                continue;
            }
            if (marker == '[') {
                int close = source.indexOf(']', index + 1);
                if (close < 0) {
                    throw new IllegalArgumentException("Unclosed index in data path: " + source);
                }
                String token = source.substring(index + 1, close).trim();
                if ((token.startsWith("\"") && token.endsWith("\""))
                        || (token.startsWith("'") && token.endsWith("'"))) {
                    segments.add(new Key(token.substring(1, token.length() - 1)));
                } else {
                    try {
                        segments.add(new Index(Integer.parseInt(token)));
                    } catch (NumberFormatException error) {
                        throw new IllegalArgumentException("Only literal data indexes are supported: " + source, error);
                    }
                }
                index = close + 1;
                continue;
            }
            throw new IllegalArgumentException("Unexpected data path token at " + index + ": " + source);
        }
        return new DataPath(segments, source);
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$' || value == '-';
    }

    public sealed interface Segment permits Key, Index {
    }

    public record Key(String name) implements Segment {
    }

    public record Index(int value) implements Segment {
    }
}
