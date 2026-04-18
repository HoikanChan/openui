import { z } from "zod";

export const ColOptionsSchema = z
  .object({
    sortable: z.boolean().optional(),
    filterable: z.boolean().optional(),
    filterOptions: z.array(z.string()).optional(),
    cell: z.any().optional(),
    format: z.enum(["date", "dateTime", "time"]).optional(),
    tooltip: z.boolean().optional(),
  })
  .optional();

export const ColSchema = z.object({
  title: z.string(),
  field: z.string(),
  options: ColOptionsSchema,
});

export const TableSchema = z.object({
  columns: z.array(ColSchema),
  rows: z.array(z.record(z.string(), z.any())),
  style: z.record(z.string(), z.any()).optional(),
});
