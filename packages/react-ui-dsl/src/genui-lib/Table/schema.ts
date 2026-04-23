import type { ElementNode } from "@openuidev/react-lang";
import type { ReactNode } from "react";
import { z } from "zod";

export type ColCellRenderer = (value: unknown, record: unknown) => ReactNode;

export interface ColOptions {
  sortable?: boolean;
  filterable?: boolean;
  filterOptions?: string[];
  cell?: ColCellRenderer | ElementNode;
  format?: "date" | "dateTime" | "time";
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
    format: z.enum(["date", "dateTime", "time"]).optional(),
    tooltip: z.boolean().optional(),
  })
  .optional();

export const ColSchema: z.ZodType<ColProps> = z.object({
  title: z.string(),
  field: z.string(),
  options: ColOptionsSchema,
});

export const TableSchema = z.object({
  columns: z.array(ColSchema),
  rows: z.array(z.record(z.string(), z.any())),
});
