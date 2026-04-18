"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Table as AntTable, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import { z } from "zod";
import { ColSchema, TableSchema } from "./schema";

function formatCell(value: unknown, format?: "date" | "dateTime" | "time"): string {
  if (value == null) return "";
  if (format === "date" || format === "dateTime" || format === "time") {
    const d = new Date(value as string);
    if (isNaN(d.getTime())) return String(value);
    if (format === "time") return d.toLocaleTimeString();
    if (format === "date") return d.toLocaleDateString();
    return d.toLocaleString();
  }
  return String(value);
}

export const Col = defineComponent({
  name: "Col",
  props: ColSchema,
  description:
    "Declarative table column. Use Col(title, field, options?) instead of writing a raw columns JSON object.",
  component: () => null,
});

type TableRow = Record<string, unknown>;

function toAntColumn(
  col: { title: string; field: string; options?: z.infer<typeof ColSchema>["options"] },
  renderNode: (value: unknown) => React.ReactNode,
): ColumnType<TableRow> {
  const options = col.options ?? {};

  return {
    title: col.title,
    dataIndex: col.field,
    key: col.field,
    sorter: options.sortable
      ? (a: TableRow, b: TableRow) =>
          String(a[col.field] ?? "").localeCompare(String(b[col.field] ?? ""))
      : undefined,
    filters:
      options.filterable && options.filterOptions
        ? options.filterOptions.map((o) => ({ text: o, value: o }))
        : undefined,
    onFilter: options.filterable
      ? (value: unknown, record: TableRow) => String(record[col.field]) === String(value)
      : undefined,
    render: (value: unknown) => {
      if (options.cell) {
        return renderNode(options.cell);
      }

      const text = formatCell(value, options.format);
      if (options.tooltip) {
        return (
          <Tooltip title={text}>
            <span>{text}</span>
          </Tooltip>
        );
      }

      return text;
    },
  };
}

export function mapColumnsToAntd(
  columns: Array<{ title: string; field: string; options?: z.infer<typeof ColSchema>["options"] }>,
  renderNode: (value: unknown) => React.ReactNode,
): ColumnType<TableRow>[] {
  return columns.map((col) => toAntColumn(col, renderNode));
}

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description: "Data table authored as Table(columns, rows, style?) with Col(title, field, options?).",
  component: ({ props, renderNode }) => {
    const antColumns = mapColumnsToAntd(props.columns, renderNode);

    return (
      <AntTable
        columns={antColumns}
        dataSource={props.rows}
        rowKey={(_, i) => String(i)}
        style={props.style as React.CSSProperties}
        pagination={false}
        size="middle"
      />
    );
  },
});
