"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import type { DescFieldProps, DescGroupProps } from "../schema";

export type DescriptionFormat = DescFieldProps["format"];

const DEFAULT_COLUMNS = 3;
const DEFAULT_GAP = 12;
const DEFAULT_LINE_HEIGHT = 24;
const DEFAULT_FONT_SIZE = 18;
const DEFAULT_ASCII_CHAR_WIDTH = 9;
const DEFAULT_WIDE_CHAR_WIDTH = 17;

const wrapperStyle: CSSProperties = {
  fontFamily:
    "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
};

const headerStyle: CSSProperties = {
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: 12,
  marginBottom: 12,
};

const titleStyle: CSSProperties = {
  fontSize: 16,
  fontWeight: 600,
  color: "#1a1a1a",
};

const groupTitleStyle: CSSProperties = {
  fontSize: 14,
  fontWeight: 600,
  color: "#1a1a1a",
  marginBottom: 10,
};

const cardStyle: CSSProperties = {
  padding: "20px 24px",
  borderRadius: 12,
  backgroundColor: "#f0f4f8",
  boxShadow: "0 1px 6px rgba(0, 0, 0, 0.06)",
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  gap: 10,
  minWidth: 0,
};

const labelStyle: CSSProperties = {
  fontSize: 13,
  fontWeight: 400,
  color: "#999999",
  lineHeight: 1,
};

const valueTextStyle: CSSProperties = {
  fontSize: DEFAULT_FONT_SIZE,
  fontWeight: 500,
  color: "#1a1a1a",
  lineHeight: 1.4,
  whiteSpace: "nowrap",
  overflow: "hidden",
  textOverflow: "ellipsis",
};

export type ResolvedDescriptionsField = {
  kind: "field";
  label: string;
  renderedValue: ReactNode;
  resolvedSpan: number;
};

export type ResolvedDescriptionsGroup = {
  kind: "group";
  columns: number;
  fields: ResolvedDescriptionsField[];
  title: string;
};

export type ResolvedDescriptionsItem = ResolvedDescriptionsField | ResolvedDescriptionsGroup;

export type DescriptionsViewProps = {
  columns?: number;
  extra?: ReactNode;
  items: ResolvedDescriptionsItem[];
  title?: string;
};

function isRenderableNode(value: unknown): boolean {
  return React.isValidElement(value);
}

export function formatDescriptionValue(value: unknown, format?: DescriptionFormat): ReactNode {
  if (isRenderableNode(value)) return value as ReactNode;
  if (value == null || value === "") return "-";

  if (format === "date" || format === "dateTime" || format === "time") {
    const date = new Date(value as string);
    if (!Number.isNaN(date.getTime())) {
      if (format === "time") return date.toLocaleTimeString();
      if (format === "date") return date.toLocaleDateString();
      return date.toLocaleString();
    }
  }

  return String(value);
}

export function resolveDescriptionAutoSpanValue(value: unknown, format?: DescriptionFormat): unknown {
  if (isRenderableNode(value)) return value;
  if (typeof value === "object" && value !== null) return value;
  return formatDescriptionValue(value, format);
}

function estimateTextWidth(value: string): number {
  return Array.from(value).reduce(
    (sum, char) => sum + (char.charCodeAt(0) > 255 ? DEFAULT_WIDE_CHAR_WIDTH : DEFAULT_ASCII_CHAR_WIDTH),
    0,
  );
}

export function resolveAutoSpan(
  value: unknown,
  columnWidth: number,
  columns = DEFAULT_COLUMNS,
  gap = DEFAULT_GAP,
): number {
  if (
    !Number.isFinite(columnWidth) ||
    columnWidth <= 0 ||
    isRenderableNode(value) ||
    (typeof value === "object" && value !== null)
  ) {
    return 1;
  }

  const text = String(value ?? "");
  if (!text) return 1;

  const estimatedWidth = estimateTextWidth(text);

  for (let span = 1; span <= columns; span++) {
    const availableWidth = columnWidth * span + gap * (span - 1);
    if (estimatedWidth <= availableWidth) return span;
  }

  return columns;
}

function renderField(field: ResolvedDescriptionsField, key: string) {
  return (
    <div
      key={key}
      style={{
        ...cardStyle,
        gridColumn: field.resolvedSpan > 1 ? `span ${field.resolvedSpan}` : undefined,
      }}
    >
      <span style={labelStyle}>{field.label}</span>
      <span style={valueTextStyle}>{field.renderedValue}</span>
    </div>
  );
}

function renderGrid(fields: ResolvedDescriptionsField[], columns: number, key?: string) {
  return (
    <div
      key={key}
      data-descriptions-columns={columns}
      style={{
        display: "grid",
        gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
        gap: DEFAULT_GAP,
      }}
    >
      {fields.map((field, index) => renderField(field, `${field.label}-${index}`))}
    </div>
  );
}

export function DescriptionsView({ columns = DEFAULT_COLUMNS, extra, items, title }: DescriptionsViewProps) {
  const sections: ReactNode[] = [];
  let pendingFields: ResolvedDescriptionsField[] = [];

  const flushPendingFields = () => {
    if (pendingFields.length === 0) return;
    sections.push(renderGrid(pendingFields, columns, `top-level-fields-${sections.length}`));
    pendingFields = [];
  };

  items.forEach((item, index) => {
    if (item.kind === "field") {
      pendingFields.push(item);
      return;
    }

    flushPendingFields();
    sections.push(
      <section key={`${item.title}-${index}`}>
        <div style={groupTitleStyle}>{item.title}</div>
        {renderGrid(item.fields, item.columns)}
      </section>,
    );
  });

  flushPendingFields();

  return (
    <div data-descriptions-columns={columns} style={wrapperStyle}>
      {(title || extra) && (
        <div style={headerStyle}>
          <div style={titleStyle}>{title}</div>
          {extra ? <div>{extra}</div> : null}
        </div>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: DEFAULT_GAP }}>
        {sections}
      </div>
    </div>
  );
}

type RuntimeDescriptionsField = DescFieldProps & { kind?: "field" };
type RuntimeDescriptionsGroup = DescGroupProps & { kind?: "group" };

export type DescriptionsRuntimeViewProps = {
  columns?: number;
  extra?: ReactNode;
  items: (RuntimeDescriptionsField | RuntimeDescriptionsGroup)[];
  renderValue: (field: DescFieldProps, resolvedSpan: number) => ResolvedDescriptionsField;
  title?: string;
};

function isGroup(value: RuntimeDescriptionsField | RuntimeDescriptionsGroup): value is RuntimeDescriptionsGroup {
  return "fields" in value;
}

export function DescriptionsRuntimeView({
  columns = DEFAULT_COLUMNS,
  extra,
  items,
  renderValue,
  title,
}: DescriptionsRuntimeViewProps) {
  const gridRef = useRef<HTMLDivElement | null>(null);
  const [gridWidth, setGridWidth] = useState(0);

  useEffect(() => {
    const element = gridRef.current;
    if (!element || typeof ResizeObserver === "undefined") return;

    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width ?? 0;
      setGridWidth(nextWidth);
    });

    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const columnWidth = useMemo(() => {
    if (gridWidth <= 0) return 0;
    return (gridWidth - DEFAULT_GAP * (columns - 1)) / columns;
  }, [columns, gridWidth]);

  const resolvedItems = useMemo<ResolvedDescriptionsItem[]>(() => {
    return items.map((item) => {
      if (isGroup(item)) {
        const groupColumns = item.columns ?? columns;
        return {
          kind: "group",
          title: item.title,
          columns: groupColumns,
          fields: item.fields.map((field) => {
            const resolvedSpan =
              field.span ?? resolveAutoSpan(resolveDescriptionAutoSpanValue(field.value, field.format), columnWidth, groupColumns);
            return renderValue(field, resolvedSpan);
          }),
        };
      }

      const resolvedSpan =
        item.span ?? resolveAutoSpan(resolveDescriptionAutoSpanValue(item.value, item.format), columnWidth, columns);
      return renderValue(item, resolvedSpan);
    });
  }, [columnWidth, columns, items, renderValue]);

  return (
    <div ref={gridRef}>
      <DescriptionsView columns={columns} extra={extra} items={resolvedItems} title={title} />
    </div>
  );
}

export const descriptionsViewTokens = {
  columns: DEFAULT_COLUMNS,
  fontSize: DEFAULT_FONT_SIZE,
  gap: DEFAULT_GAP,
  lineHeight: DEFAULT_LINE_HEIGHT,
} as const;
