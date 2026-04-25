"use client";

import React, { Fragment, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import type { DescFieldProps, DescGroupProps } from "../schema";
export type DescriptionsVariant = "bordered" | "plain";

const DEFAULT_COLUMNS = 3;
const DEFAULT_GAP = 12;
const PLAIN_COLUMN_GAP = 40;
const PLAIN_ROW_GAP = 18;
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
  marginBottom: 16,
};

const titleStyle: CSSProperties = {
  fontSize: 16,
  fontWeight: 600,
  color: "#1f1f1f",
};

const groupTitleStyle: CSSProperties = {
  fontSize: 14,
  fontWeight: 600,
  color: "#1f1f1f",
  marginBottom: 12,
};

const borderedGridStyle = (columns: number): CSSProperties => ({
  display: "flex",
  flexDirection: "column",
  border: "1px solid #f0f0f0",
  borderRadius: 8,
  overflow: "hidden",
  backgroundColor: "#ffffff",
});

const borderedRowStyle = (columns: number, isLastRow: boolean): CSSProperties => ({
  display: "grid",
  gridTemplateColumns: `repeat(${columns * 2}, minmax(0, 1fr))`,
  borderBottom: isLastRow ? undefined : "1px solid #f0f0f0",
});

const borderedLabelCellStyle: CSSProperties = {
  padding: "16px 24px",
  backgroundColor: "#fafafa",
  borderRight: "1px solid #f0f0f0",
  color: "#8c8c8c",
  fontSize: 14,
  lineHeight: 1.5715,
};

const borderedValueCellStyle: CSSProperties = {
  padding: "16px 24px",
  backgroundColor: "#ffffff",
  borderRight: "1px solid #f0f0f0",
  color: "#262626",
  fontSize: 14,
  lineHeight: 1.5715,
  minWidth: 0,
  overflowWrap: "anywhere",
};

const plainGridStyle = (columns: number): CSSProperties => ({
  display: "grid",
  gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
  columnGap: PLAIN_COLUMN_GAP,
  rowGap: PLAIN_ROW_GAP,
});

const plainFieldStyle: CSSProperties = {
  display: "flex",
  alignItems: "baseline",
  gap: 8,
  minWidth: 0,
  flexWrap: "wrap",
};

const plainLabelStyle: CSSProperties = {
  color: "#bfbfbf",
  fontSize: 14,
  lineHeight: 1.5715,
  whiteSpace: "nowrap",
};

const plainValueStyle: CSSProperties = {
  color: "#1f1f1f",
  fontSize: 14,
  lineHeight: 1.5715,
  minWidth: 0,
  overflowWrap: "anywhere",
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
  border?: boolean;
  columns?: number;
  extra?: ReactNode;
  items: ResolvedDescriptionsItem[];
  title?: string;
};

function isRenderableNode(value: unknown): boolean {
  return React.isValidElement(value);
}

function getVariant(border?: boolean): DescriptionsVariant {
  return border === false ? "plain" : "bordered";
}

function normalizeDescriptionContent(value: ReactNode): ReactNode {
  if (Array.isArray(value)) {
    return value.map((child, index) => <Fragment key={index}>{normalizeDescriptionContent(child)}</Fragment>);
  }

  if (!React.isValidElement(value)) return value;

  const element = value as React.ReactElement<{ children?: ReactNode; style?: CSSProperties }>;
  const tagName = typeof element.type === "string" ? element.type : null;
  const normalizedChildren =
    element.props.children == null ? element.props.children : React.Children.map(element.props.children, normalizeDescriptionContent);

  let style = element.props.style;

  if (tagName === "ul" || tagName === "ol") {
    style = {
      listStyle: "none",
      margin: 0,
      paddingInlineStart: 0,
      paddingLeft: 0,
      ...style,
    };
  } else if (tagName === "li") {
    style = {
      listStyle: "none",
      margin: 0,
      ...style,
    };
  }

  return React.cloneElement(element, { style }, normalizedChildren);
}

export function formatDescriptionValue(value: unknown): ReactNode {
  if (isRenderableNode(value)) return value as ReactNode;
  if (value == null || value === "") return "-";

  return String(value);
}

export function resolveDescriptionAutoSpanValue(value: unknown): unknown {
  if (isRenderableNode(value)) return value;
  if (typeof value === "object" && value !== null) return value;
  return formatDescriptionValue(value);
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

function renderBorderedGrid(fields: ResolvedDescriptionsField[], columns: number, key?: string) {
  const rows: ResolvedDescriptionsField[][] = [];
  let currentRow: ResolvedDescriptionsField[] = [];
  let usedColumns = 0;

  fields.forEach((field) => {
    const fieldSpan = Math.max(1, Math.min(field.resolvedSpan, columns));

    if (usedColumns + fieldSpan > columns) {
      rows.push(currentRow);
      currentRow = [field];
      usedColumns = fieldSpan;
      return;
    }

    currentRow.push(field);
    usedColumns += fieldSpan;
  });

  if (currentRow.length > 0) rows.push(currentRow);

  return (
    <div
      key={key}
      data-descriptions-columns={columns}
      data-descriptions-layout="bordered"
      style={borderedGridStyle(columns)}
    >
      {rows.map((row, rowIndex) => (
        <div
          key={`bordered-row-${rowIndex}`}
          data-descriptions-row="bordered"
          style={borderedRowStyle(columns, rowIndex === rows.length - 1)}
        >
          {row.map((field, index) => {
            const fieldSpan = Math.max(1, Math.min(field.resolvedSpan, columns));
            const valueSpan = Math.max(1, fieldSpan * 2 - 1);
            const isLastFieldInRow = index === row.length - 1;

            return (
              <Fragment key={`${field.label}-${rowIndex}-${index}`}>
                <div
                  style={{
                    ...borderedLabelCellStyle,
                    borderRight: isLastFieldInRow && valueSpan === columns * 2 - 1 ? undefined : borderedLabelCellStyle.borderRight,
                  }}
                >
                  {field.label}
                </div>
                <div
                  style={{
                    ...borderedValueCellStyle,
                    borderRight: isLastFieldInRow ? undefined : borderedValueCellStyle.borderRight,
                    gridColumn: valueSpan > 1 ? `span ${valueSpan}` : undefined,
                  }}
                >
                  {normalizeDescriptionContent(field.renderedValue)}
                </div>
              </Fragment>
            );
          })}
        </div>
      ))}
    </div>
  );
}

function renderPlainField(field: ResolvedDescriptionsField, key: string) {
  return (
    <div
      key={key}
      style={{
        ...plainFieldStyle,
        gridColumn: field.resolvedSpan > 1 ? `span ${field.resolvedSpan}` : undefined,
      }}
    >
      <span style={plainLabelStyle}>{field.label} :</span>
      <span style={plainValueStyle}>{normalizeDescriptionContent(field.renderedValue)}</span>
    </div>
  );
}

function renderPlainGrid(fields: ResolvedDescriptionsField[], columns: number, key?: string) {
  return (
    <div
      key={key}
      data-descriptions-columns={columns}
      data-descriptions-layout="plain"
      style={plainGridStyle(columns)}
    >
      {fields.map((field, index) => renderPlainField(field, `${field.label}-${index}`))}
    </div>
  );
}

function renderGrid(
  fields: ResolvedDescriptionsField[],
  columns: number,
  variant: DescriptionsVariant,
  key?: string,
) {
  if (variant === "plain") return renderPlainGrid(fields, columns, key);
  return renderBorderedGrid(fields, columns, key);
}

export function DescriptionsView({
  border = true,
  columns = DEFAULT_COLUMNS,
  extra,
  items,
  title,
}: DescriptionsViewProps) {
  const variant = getVariant(border);
  const sections: ReactNode[] = [];
  let pendingFields: ResolvedDescriptionsField[] = [];

  const flushPendingFields = () => {
    if (pendingFields.length === 0) return;
    sections.push(renderGrid(pendingFields, columns, variant, `top-level-fields-${sections.length}`));
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
        {renderGrid(item.fields, item.columns, variant)}
      </section>,
    );
  });

  flushPendingFields();

  return (
    <div data-descriptions-columns={columns} data-descriptions-variant={variant} style={wrapperStyle}>
      {(title || extra) && (
        <div style={headerStyle}>
          <div style={titleStyle}>{title}</div>
          {extra ? <div>{extra}</div> : null}
        </div>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: variant === "plain" ? 20 : DEFAULT_GAP }}>
        {sections}
      </div>
    </div>
  );
}

type RuntimeDescriptionsField = DescFieldProps & { kind?: "field" };
type RuntimeDescriptionsGroup = DescGroupProps & { kind?: "group" };

export type DescriptionsRuntimeViewProps = {
  border?: boolean;
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
  border = true,
  columns = DEFAULT_COLUMNS,
  extra,
  items,
  renderValue,
  title,
}: DescriptionsRuntimeViewProps) {
  const gridRef = useRef<HTMLDivElement | null>(null);
  const [gridWidth, setGridWidth] = useState(0);
  const variant = getVariant(border);

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

  const columnGap = variant === "plain" ? PLAIN_COLUMN_GAP : DEFAULT_GAP;

  const columnWidth = useMemo(() => {
    if (gridWidth <= 0) return 0;
    return (gridWidth - columnGap * (columns - 1)) / columns;
  }, [columnGap, columns, gridWidth]);

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
              field.span ??
              resolveAutoSpan(resolveDescriptionAutoSpanValue(field.value), columnWidth, groupColumns, columnGap);
            return renderValue(field, resolvedSpan);
          }),
        };
      }

      const resolvedSpan =
        item.span ??
        resolveAutoSpan(resolveDescriptionAutoSpanValue(item.value), columnWidth, columns, columnGap);
      return renderValue(item, resolvedSpan);
    });
  }, [columnGap, columnWidth, columns, items, renderValue]);

  return (
    <div ref={gridRef}>
      <DescriptionsView border={border} columns={columns} extra={extra} items={resolvedItems} title={title} />
    </div>
  );
}

export const descriptionsViewTokens = {
  columns: DEFAULT_COLUMNS,
  fontSize: DEFAULT_FONT_SIZE,
  gap: DEFAULT_GAP,
  lineHeight: DEFAULT_LINE_HEIGHT,
} as const;
