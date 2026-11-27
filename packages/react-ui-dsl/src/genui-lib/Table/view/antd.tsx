"use client";

import { Table as AntTable, Tooltip } from "antd";
import type { ColumnType } from "antd/es/table";
import type { ReactNode } from "react";
import {
  formatCell,
  getColumnProps,
  getFieldValue,
  isElementNode,
  renderExpandRow,
} from "./shared";
import type { ColumnValue, TableRow, TableViewProps } from "./types";

export { formatCell };

export function mapColumnsToAntd(
  columns: ColumnValue[],
  renderNode?: (value: unknown) => ReactNode,
): ColumnType<TableRow>[] {
  return columns.map((columnValue) => {
    const column = getColumnProps(columnValue);
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
      render: (value: unknown, record: TableRow) => {
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
