"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Table as AntTable, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import { TableSchema } from "./schema";

function formatCell(value: unknown, format?: "data" | "dateTime" | "time"): string {
  if (value == null) return "";
  if (format === "dateTime" || format === "time") {
    const d = new Date(value as string);
    if (isNaN(d.getTime())) return String(value);
    return format === "time" ? d.toLocaleTimeString() : d.toLocaleString();
  }
  return String(value);
}

export const Table = defineComponent({
  name: "Table",
  props: TableSchema,
  description: "Data table with column definitions",
  component: ({ props, renderNode }) => {
    const { columns } = props.properties;

    const antColumns: ColumnType<Record<string, unknown>>[] = columns.map((col) => ({
      title: col.title,
      dataIndex: col.field,
      key: col.field,
      sorter: col.sortable
        ? (a: Record<string, unknown>, b: Record<string, unknown>) =>
            String(a[col.field] ?? "").localeCompare(String(b[col.field] ?? ""))
        : undefined,
      filters:
        col.filterable && col.filterOptions
          ? col.filterOptions.map((o) => ({ text: o, value: o }))
          : undefined,
      onFilter: col.filterable
        ? (value: unknown, record: Record<string, unknown>) =>
            String(record[col.field]) === String(value)
        : undefined,
      render: (value: unknown) => {
        if (col.customized) {
          return renderNode(col.customized);
        }
        const text = formatCell(value, col.format);
        if (col.tooltip) {
          return <Tooltip title={text}><span>{text}</span></Tooltip>;
        }
        return text;
      },
    }));

    return (
      <AntTable
        columns={antColumns}
        dataSource={[]}
        rowKey={(_, i) => String(i)}
        style={props.style as React.CSSProperties}
        pagination={false}
        size="middle"
      />
    );
  },
});
