/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Minimal classpath prompt renderer with raw variable insertion, one-level conditions, and list blocks.
 *
 * <p>
 * Supported syntax:
 * <ul>
 * <li>{@code {{name}}} inserts the raw, unescaped value once.</li>
 * <li>{@code {{#if flag}}...{{else}}...{{/if}}} renders one branch; {@code else} is optional and conditions may
 * nest. Conditions accept Boolean, nonblank text, nonempty collections/maps, or null (false).</li>
 * <li>{@code {{#for items}}...{{/for}}} repeats its body for each item; {@code items} must be a
 * {@code Collection<Map<String, ?>>}. A null or empty collection renders nothing. Blocks must not nest, but
 * {@code #if} may appear inside {@code #for} and {@code #for} may appear inside {@code #if}.</li>
 * </ul>
 *
 * <p>
 * Inside a {@code #for} body variables and conditions resolve against the current item first and then the outer
 * model. Resources are decoded as strict UTF-8, line endings are normalized to {@code \n}, whitespace is preserved
 * exactly, and parsed templates are cached per resource. The whole template is parsed before any rendering starts, so
 * variables in an unselected branch or an empty loop are never required. Invalid syntax, missing resources, missing
 * or {@code null} variables, unsupported condition types, and unsupported loop values raise
 * {@link GenerationSdkException} with the resource and position or variable name.
 *
 * @since 2026
 */
public final class PromptRenderer {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Map<String, Template> CACHE = new ConcurrentHashMap<>();

    private PromptRenderer() {
    }

    /**
     * Renders a classpath prompt template.
     *
     * @param resource classpath resource name, for example {@code prompts/system.md}
     * @param model variable values; branch conditions accept Boolean, text, collections/maps, or null
     * @return rendered text with LF line endings
     * @throws GenerationSdkException if the resource is missing or not valid UTF-8, the template is invalid, or a
     * required variable is missing, {@code null}, or has an unsupported type
     */
    public static String render(String resource, Map<String, ?> model) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(model, "model");
        return CACHE.computeIfAbsent(resource, PromptRenderer::load).render(model);
    }

    private static Template load(String resource) {
        byte[] bytes = readResource(resource);
        String source = normalizeLineEndings(decodeUtf8(resource, bytes));
        return new Template(resource, source, parse(resource, source));
    }

    private static byte[] readResource(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PromptRenderer.class.getClassLoader();
        }
        if (loader == null) {
            throw new GenerationSdkException(
                    "Cannot load prompt template resource '" + resource + "': no class loader available");
        }
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new GenerationSdkException("Prompt template resource not found: " + resource);
            }
            return input.readAllBytes();
        } catch (IOException error) {
            throw new GenerationSdkException("Failed to read prompt template resource: " + resource, error);
        }
    }

    private static String decodeUtf8(String resource, byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new GenerationSdkException("Prompt template resource is not valid UTF-8: " + resource, error);
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<Node> parse(String resource, String source) {
        List<Token> tokens = tokenize(resource, source);
        return new Parser(resource, source, tokens).parseUntil(Set.of(), false, false).nodes();
    }

    private static List<Token> tokenize(String resource, String source) {
        List<Token> tokens = new ArrayList<>();
        int cursor = 0;
        while (cursor < source.length()) {
            int open = source.indexOf("{{", cursor);
            if (open < 0) {
                tokens.add(new TextToken(source.substring(cursor)));
                break;
            }
            int close = source.indexOf("}}", open + 2);
            if (close < 0) {
                throw error(resource, source, open, "unclosed tag");
            }
            String content = source.substring(open + 2, close).trim();
            TagToken tag = classify(resource, source, content, open);
            int lineStart = source.lastIndexOf('\n', open - 1) + 1;
            int lineEnd = source.indexOf('\n', close + 2);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            boolean standalone = tag.type() != TagType.VARIABLE && lineStart >= cursor
                    && source.substring(lineStart, open).isBlank()
                    && source.substring(close + 2, lineEnd).isBlank();
            int textEnd = standalone ? lineStart : open;
            if (textEnd > cursor) {
                tokens.add(new TextToken(source.substring(cursor, textEnd)));
            }
            tokens.add(tag);
            cursor = standalone ? Math.min(lineEnd + 1, source.length()) : close + 2;
        }
        return tokens;
    }

    private static TagToken classify(String resource, String source, String content, int offset) {
        if (content.isEmpty()) {
            throw error(resource, source, offset, "empty tag");
        }
        if (content.equals("else")) {
            return new TagToken(TagType.ELSE, content, offset);
        }
        if (content.equals("/if")) {
            return new TagToken(TagType.END_IF, content, offset);
        }
        if (content.equals("/for")) {
            return new TagToken(TagType.END_FOR, content, offset);
        }
        if (content.equals("#if")) {
            throw error(resource, source, offset, "invalid #if condition \"" + content + "\"");
        }
        if (content.startsWith("#if") && content.length() > 3 && Character.isWhitespace(content.charAt(3))) {
            String condition = content.substring(3).trim();
            if (!isIdentifier(condition)) {
                throw error(resource, source, offset, "invalid #if condition \"" + content + "\"");
            }
            return new TagToken(TagType.IF, condition, offset);
        }
        if (content.equals("#for")) {
            throw error(resource, source, offset, "invalid #for variable \"" + content + "\"");
        }
        if (content.startsWith("#for") && content.length() > 4 && Character.isWhitespace(content.charAt(4))) {
            String variable = content.substring(4).trim();
            if (!isIdentifier(variable)) {
                throw error(resource, source, offset, "invalid #for variable \"" + content + "\"");
            }
            return new TagToken(TagType.FOR, variable, offset);
        }
        if (content.startsWith("#")) {
            throw error(resource, source, offset, "unknown tag \"" + content + "\"");
        }
        if (content.startsWith("/")) {
            throw error(resource, source, offset, "unknown closing tag \"" + content + "\"");
        }
        if (!isIdentifier(content)) {
            throw error(resource, source, offset, "invalid tag \"" + content + "\"");
        }
        return new TagToken(TagType.VARIABLE, content, offset);
    }

    private static boolean isIdentifier(String value) {
        return IDENTIFIER.matcher(value).matches();
    }

    private static String renderNodes(List<Node> nodes, RenderContext context) {
        StringBuilder out = new StringBuilder();
        for (Node node : nodes) {
            if (node instanceof TextNode text) {
                out.append(text.text());
            } else if (node instanceof VariableNode variable) {
                out.append(value(context, variable.offset(), variable.name()));
            } else if (node instanceof IfNode block) {
                boolean selected = condition(context, block.offset(), block.condition());
                out.append(renderNodes(selected ? block.thenNodes() : block.elseNodes(), context));
            } else if (node instanceof ForNode block) {
                out.append(renderFor(block, context));
            }
        }
        return out.toString();
    }

    private static String renderFor(ForNode block, RenderContext context) {
        if (!contains(context, block.name())) {
            throw error(context.resource(), context.source(), block.offset(),
                    "missing variable '" + block.name() + "'");
        }
        Object candidate = lookup(context, block.name());
        if (candidate == null) {
            return "";
        }
        if (!(candidate instanceof Collection<?> items)) {
            throw error(context.resource(), context.source(), block.offset(),
                    "#for '" + block.name() + "' must be a Collection but was " + typeName(candidate));
        }
        StringBuilder out = new StringBuilder();
        for (Object item : items) {
            out.append(renderNodes(block.body(), context.withItem(itemScope(block, context, item))));
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> itemScope(ForNode block, RenderContext context, Object item) {
        if (!(item instanceof Map<?, ?> entries)) {
            throw error(context.resource(), context.source(), block.offset(),
                    "item of #for '" + block.name() + "' must be Map<String, ?> but was " + typeName(item));
        }
        for (Object key : entries.keySet()) {
            if (!(key instanceof String)) {
                throw error(context.resource(), context.source(), block.offset(),
                        "item of #for '" + block.name() + "' must be Map<String, ?> but had key of type "
                                + typeName(key));
            }
        }
        return (Map<String, ?>) entries;
    }

    private static Object value(RenderContext context, int offset, String name) {
        if (!contains(context, name)) {
            throw error(context.resource(), context.source(), offset, "missing variable '" + name + "'");
        }
        Object value = lookup(context, name);
        if (value == null) {
            throw error(context.resource(), context.source(), offset, "variable '" + name + "' is null");
        }
        return value;
    }

    private static boolean condition(RenderContext context, int offset, String name) {
        if (!contains(context, name)) {
            throw error(context.resource(), context.source(), offset, "missing variable '" + name + "'");
        }
        Object value = lookup(context, name);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().isBlank();
        }
        if (value instanceof Collection<?> items) {
            return !items.isEmpty();
        }
        if (value instanceof Map<?, ?> entries) {
            return !entries.isEmpty();
        }
        throw error(context.resource(), context.source(), offset,
                "condition '" + name + "' must be Boolean, text, Collection, or Map but was "
                        + value.getClass().getName());
    }

    private static boolean contains(RenderContext context, String name) {
        return context.item() != null && context.item().containsKey(name) || context.model().containsKey(name);
    }

    private static Object lookup(RenderContext context, String name) {
        if (context.item() != null && context.item().containsKey(name)) {
            return context.item().get(name);
        }
        return context.model().get(name);
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static GenerationSdkException error(String resource, String source, int offset, String detail) {
        int line = 1;
        int column = 1;
        int limit = Math.min(offset, source.length());
        for (int index = 0; index < limit; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new GenerationSdkException(
                "Prompt template '" + resource + "' at line " + line + ", column " + column + ": " + detail);
    }

    private sealed interface Token permits TextToken, TagToken {
    }

    private record TextToken(String text) implements Token {
    }

    private record TagToken(TagType type, String value, int offset) implements Token {
    }

    private enum TagType {
        VARIABLE, IF, ELSE, END_IF, FOR, END_FOR
    }

    private sealed interface Node permits TextNode, VariableNode, IfNode, ForNode {
    }

    private record TextNode(String text) implements Node {
    }

    private record VariableNode(String name, int offset) implements Node {
    }

    private record IfNode(String condition, int offset, List<Node> thenNodes, List<Node> elseNodes) implements Node {
    }

    private record ForNode(String name, int offset, List<Node> body) implements Node {
    }

    private record Block(List<Node> nodes, TagToken terminator) {
    }

    private record RenderContext(String resource, String source, Map<String, ?> model, Map<String, ?> item) {
        RenderContext withItem(Map<String, ?> scope) {
            return new RenderContext(resource, source, model, scope);
        }
    }

    private static final class Parser {
        private final String resource;
        private final String source;
        private final List<Token> tokens;
        private int cursor;

        private Parser(String resource, String source, List<Token> tokens) {
            this.resource = resource;
            this.source = source;
            this.tokens = tokens;
        }

        private Block parseUntil(Set<TagType> terminators, boolean insideIf, boolean insideFor) {
            List<Node> nodes = new ArrayList<>();
            while (cursor < tokens.size()) {
                Token token = tokens.get(cursor++);
                if (token instanceof TextToken text) {
                    nodes.add(new TextNode(text.text()));
                    continue;
                }
                TagToken tag = (TagToken) token;
                if (terminators.contains(tag.type())) {
                    return new Block(nodes, tag);
                }
                switch (tag.type()) {
                    case VARIABLE -> nodes.add(new VariableNode(tag.value(), tag.offset()));
                    case IF -> nodes.add(parseIf(tag, insideFor));
                    case FOR -> {
                        if (insideFor) {
                            throw error(resource, source, tag.offset(), "nested #for blocks are not supported");
                        }
                        nodes.add(parseFor(tag));
                    }
                    case ELSE -> throw error(resource, source, tag.offset(),
                            insideIf ? "duplicate {{else}}" : "{{else}} without an open {{#if}}");
                    case END_IF -> throw error(resource, source, tag.offset(),
                            "{{/if}} without an open {{#if}}");
                    case END_FOR -> throw error(resource, source, tag.offset(),
                            "{{/for}} without an open {{#for}}");
                }
            }
            return new Block(nodes, null);
        }

        private Node parseIf(TagToken tag, boolean insideFor) {
            Block thenBlock = parseUntil(Set.of(TagType.ELSE, TagType.END_IF), true, insideFor);
            if (thenBlock.terminator() == null) {
                throw error(resource, source, tag.offset(), "unclosed {{#if " + tag.value() + "}}");
            }
            List<Node> elseNodes = List.of();
            if (thenBlock.terminator().type() == TagType.ELSE) {
                Block elseBlock = parseUntil(Set.of(TagType.END_IF), true, insideFor);
                if (elseBlock.terminator() == null) {
                    throw error(resource, source, tag.offset(), "unclosed {{#if " + tag.value() + "}}");
                }
                elseNodes = elseBlock.nodes();
            }
            return new IfNode(tag.value(), tag.offset(), thenBlock.nodes(), elseNodes);
        }

        private Node parseFor(TagToken tag) {
            Block body = parseUntil(Set.of(TagType.END_FOR), false, true);
            if (body.terminator() == null) {
                throw error(resource, source, tag.offset(), "unclosed {{#for " + tag.value() + "}}");
            }
            return new ForNode(tag.value(), tag.offset(), body.nodes());
        }
    }

    private record Template(String resource, String source, List<Node> nodes) {
        String render(Map<String, ?> model) {
            return renderNodes(nodes, new RenderContext(resource, source, model, null));
        }
    }
}
