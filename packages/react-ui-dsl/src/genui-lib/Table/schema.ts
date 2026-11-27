import type { ElementNode } from "@cloudsop/openui-react-lang";
import type { ReactNode } from "react";
import { z } from "zod";

export type ColCellRenderer = (value: unknown, record: unknown) => ReactNode;
export type ExpandRowRenderer = (record: unknown) => ReactNode;

export interface ColOptions {
  sortable?: boolean;
  filterable?: boolean;
  filterOptions?: string[];
  cell?: ColCellRenderer | ElementNode;
  tooltip?: boolean;
}

export interface ColProps {
  title: string;
  field: string;
  options?: ColOptions;
}

export const ColOptionsSchema: z.ZodType<ColOptions | undefined> = z
  .object({
    sortable: z.boolean().optional(),
    filterable: z.boolean().optional(),
    filterOptions: z.array(z.string()).optional(),
    cell: z.any().optional(),
    tooltip: z.boolean().optional(),
  })
  .strict()
  .optional();

export const ColSchema: z.ZodType<ColProps> = z
  .object({
    title: z.string(),
    field: z.string(),
    options: ColOptionsSchema,
  })
  .strict();

export interface TableProps {
  columns: ColProps[];
  rows: Record<string, unknown>[];
  expandRow?: ExpandRowRenderer | ElementNode;
}

export const TableSchema: z.ZodType<TableProps> = z
  .object({
    columns: z.array(ColSchema),
    rows: z.array(z.record(z.string(), z.any())),
    expandRow: z.any().optional(),
  })
  .strict();
