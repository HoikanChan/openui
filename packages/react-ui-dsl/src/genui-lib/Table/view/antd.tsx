"use client";

import { Table as AntTable, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import type { ReactNode } from "react";
import {
  formatCell,
  getFieldValue,
  isElementNode,
  normalizeColumn,
  renderExpandRow,
} from "./shared";
import type { ColumnValue, TableViewProps } from "./types";

export { formatCell };

function renderFixedValue(
  value: unknown,
  record: Record<string, unknown>,
  renderNode?: (value: unknown) => ReactNode,
): ReactNode {
  // @Render slots arrive hydrated as functions; ExtraCol binds the current row.
  if (typeof value === "function") {
    return value(record);
  }
  if (isElementNode(value)) {
    return renderNode ? renderNode(value) : null;
  }
  return formatCell(value);
}

export function mapColumnsToAntd(
  columns: ColumnValue[],
  renderNode?: (value: unknown) => ReactNode,
): ColumnType<Record<string, unknown>>[] {
  return columns.map((columnValue, columnIndex) => {
    const column = normalizeColumn(columnValue);

    if (column.kind === "index") {
      return {
        key: `index-${columnIndex}`,
        title: column.title,
        render: (_value, _record, rowIndex) => formatCell((rowIndex ?? 0) + 1),
      };
    }

    if (column.kind === "extra") {
      return {
        key: `extra-${columnIndex}`,
        title: column.title,
        render: (_value, record) => renderFixedValue(column.value, record, renderNode),
      };
    }

    const options = column.options ?? {};

    return {
      dataIndex: column.field.includes(".") ? column.field.split(".") : column.field,
      filters:
        options.filterable && options.filterOptions
          ? options.filterOptions.map((option) => ({ text: option, value: option }))
          : undefined,
      key: column.field,
      onFilter: options.filterable
        ? (value, record) => String(getFieldValue(record, column.field)) === String(value)
        : undefined,
      ellipsis: options.tooltip ? true : undefined,
      render: (value: unknown, record: Record<string, unknown>) => {
        if (typeof options.cell === "function") {
          return options.cell(value, record);
        }

        if (options.cell && isElementNode(options.cell)) {
          return renderNode ? renderNode(options.cell) : null;
        }

        const text = formatCell(value);
        if (options.tooltip) {
          return (
            <Tooltip title={text}>
              <span
                style={{
                  display: "block",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {text}
              </span>
            </Tooltip>
          );
        }

        return text;
      },
      sorter: options.sortable
        ? (a, b) =>
            String(getFieldValue(a, column.field) ?? "").localeCompare(
              String(getFieldValue(b, column.field) ?? ""),
            )
        : undefined,
      title: column.title,
    };
  });
}

export function TableView({ columns, expandRow, renderNode, rows, style }: TableViewProps) {
  return (
    <AntTable
      columns={mapColumnsToAntd(columns, renderNode)}
      dataSource={rows}
      expandable={
        expandRow
          ? {
              defaultExpandAllRows: rows.length <= 3,
              expandedRowRender: (record) => renderExpandRow(expandRow, record, renderNode),
            }
          : undefined
      }
      pagination={false}
      rowKey={(_, index) => String(index)}
      size="middle"
      style={style}
    />
  );
}
