"use client";

import type { ElementNode } from "@cloudsop/openui-react-lang";
import type { CSSProperties, ReactNode } from "react";
import type {
  ColCellRenderer,
  ExpandRowRenderer,
  ExtraColProps,
  IndexColProps,
} from "../schema";

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

export type TableColumnViewProps = ColViewProps | ExtraColProps | IndexColProps;

export type NormalizedColumn =
  | ({ kind: "field" } & ColViewProps)
  | ({ kind: "extra" } & ExtraColProps)
  | ({ kind: "index" } & IndexColProps);

export type ColumnValue =
  | TableColumnViewProps
  | {
      props: TableColumnViewProps;
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
