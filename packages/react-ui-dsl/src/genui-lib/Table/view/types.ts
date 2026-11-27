"use client";

import type { ElementNode } from "@cloudsop/openui-react-lang";
import type { CSSProperties, ReactNode } from "react";
import type { ColCellRenderer, ExpandRowRenderer } from "../schema";

export type TableRow = Record<string, unknown>;

export type ColViewProps = {
  field: string;
  options?: {
    cell?: ColCellRenderer | ElementNode;
    filterOptions?: string[];
    filterable?: boolean;
    sortable?: boolean;
    tooltip?: boolean;
  };
  title: string;
};

export type ColumnValue =
  | ColViewProps
  | {
      props: ColViewProps;
      type: string;
      typeName?: string;
    };

export type TableViewProps = {
  columns: ColumnValue[];
  expandRow?: ExpandRowRenderer | ElementNode;
  renderNode?: (value: unknown) => ReactNode;
  rows: TableRow[];
  style?: CSSProperties;
};
