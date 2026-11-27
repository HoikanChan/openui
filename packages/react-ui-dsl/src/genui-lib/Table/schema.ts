import type { ElementNode } from "@cloudsop/openui-react-lang";
import type { ReactNode } from "react";
import { z } from "zod";
import { ExtraColSchema, type ExtraColProps } from "./ExtraCol/schema";
import { IndexColSchema, type IndexColProps } from "./IndexCol/schema";

export type { ExtraColProps } from "./ExtraCol/schema";
export type { IndexColProps } from "./IndexCol/schema";

export type ColCellRenderer = (value: unknown, record: unknown) => ReactNode;
export type ExpandRowRenderer = (record: unknown) => ReactNode;

export const ColOptionsSchema = z
  .object({
    sortable: z.boolean().optional(),
    filterable: z.boolean().optional(),
    filterOptions: z.array(z.string()).optional(),
    cell: z.any().optional(),
    tooltip: z.boolean().optional(),
  })
  .strict();

export type ColOptions = z.infer<typeof ColOptionsSchema>;

export const ColSchema = z
  .object({
    title: z.string(),
    field: z.string(),
    options: ColOptionsSchema.optional(),
  })
  .strict();

export type ColProps = z.infer<typeof ColSchema>;

export type TableColumnProps = ColProps | ExtraColProps | IndexColProps;

export const TableSchema = z
  .object({
    columns: z.array(z.union([ColSchema, ExtraColSchema, IndexColSchema])),
    rows: z.array(z.record(z.string(), z.any())),
    expandRow: z.any().optional(),
  })
  .strict();

export type TableProps = z.infer<typeof TableSchema>;
