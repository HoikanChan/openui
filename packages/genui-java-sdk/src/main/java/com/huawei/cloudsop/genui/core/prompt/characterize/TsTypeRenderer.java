package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.Map;

/**
 * Renders an inferred {@link ShapeNode} schema tree to a TypeScript-type string, rooted at the name
 * {@code data} so the rendered type reinforces the {@code data.<field>} reference path used elsewhere
 * in the prompt.
 */
public final class TsTypeRenderer {
  private TsTypeRenderer() {}

  public static String render(ShapeNode root, int sampleRows) {
    StringBuilder out = new StringBuilder();
    out.append("data: ");
    renderNode(root, sampleRows, 0, out);
    return out.toString();
  }

  private static void renderNode(ShapeNode node, int sampleRows, int depth, StringBuilder out) {
    switch (node) {
      case ObjectShape object -> renderObject(object, sampleRows, depth, out);
      case ArrayShape array -> renderArray(array, sampleRows, depth, out);
      case ScalarShape scalar -> out.append(renderScalar(scalar.type()));
      case EnumShape enumShape -> out.append(renderEnum(enumShape.domain()));
    }
  }

  private static void renderObject(ObjectShape object, int sampleRows, int depth, StringBuilder out) {
    out.append("{\n");
    String indent = "  ".repeat(depth + 1);
    for (Map.Entry<String, FieldShape> entry : object.fields().entrySet()) {
      FieldShape field = entry.getValue();
      out.append(indent).append(entry.getKey());
      if (field.optional()) out.append('?');
      out.append(": ");
      renderNode(field.node(), sampleRows, depth + 1, out);
      if (field.nullable()) out.append(" | null");
      out.append('\n');
    }
    out.append("  ".repeat(depth)).append('}');
  }

  private static void renderArray(ArrayShape array, int sampleRows, int depth, StringBuilder out) {
    renderNode(array.element(), sampleRows, depth, out);
    out.append("[]");
    if (array.truncated()) {
      long shown = Math.min(sampleRows, array.count());
      out.append("  // ").append(array.count()).append(" items (showing ").append(shown).append(')');
    }
  }

  private static String renderScalar(ScalarType type) {
    return switch (type) {
      case STRING -> "string";
      case NUMBER -> "number";
      case BOOLEAN -> "boolean";
      case NULL -> "null";
      case UNKNOWN -> "unknown";
    };
  }

  private static String renderEnum(java.util.List<String> domain) {
    StringBuilder out = new StringBuilder();
    boolean first = true;
    for (String value : domain) {
      if (!first) out.append(" | ");
      first = false;
      out.append('"').append(escape(value)).append('"');
    }
    return out.toString();
  }

  private static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' || c == '"') out.append('\\');
      out.append(c);
    }
    return out.toString();
  }
}
