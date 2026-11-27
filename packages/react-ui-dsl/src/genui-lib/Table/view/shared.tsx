"use client";

import type { ElementNode } from "@cloudsop/openui-react-lang";
import type { ReactNode } from "react";
import type { ExpandRowRenderer } from "../schema";
import type { ColumnValue, ColViewProps, TableRow } from "./types";

export function formatCell(value: unknown): string {
  if (value == null) return "";
  return String(value);
}

export function isColumnNode(
  column: ColumnValue,
): column is Extract<ColumnValue, { props: ColViewProps }> {
  return typeof column === "object" && column !== null && "props" in column;
}

export function getColumnProps(column: ColumnValue): ColViewProps {
  return isColumnNode(column) ? column.props : column;
}

export function getFieldValue(record: TableRow, field: string): unknown {
  return field.split(".").reduce<unknown>((value, key) => {
    if (value && typeof value === "object") {
      return (value as Record<string, unknown>)[key];
    }
    return undefined;
  }, record);
}

export function isElementNode(value: unknown): value is ElementNode {
  return (
    typeof value === "object" &&
    value !== null &&
    (value as ElementNode).type === "element" &&
    typeof (value as ElementNode).typeName === "string" &&
    typeof (value as ElementNode).props === "object" &&
    (value as ElementNode).props !== null &&
    typeof (value as ElementNode).partial === "boolean"
  );
}

export function renderExpandRow(
  expandRow: ExpandRowRenderer | ElementNode,
  record: TableRow,
  renderNode?: (value: unknown) => ReactNode,
): ReactNode {
  if (isElementNode(expandRow)) {
    return renderNode ? renderNode(expandRow) : null;
  }
  return expandRow(record);
}
