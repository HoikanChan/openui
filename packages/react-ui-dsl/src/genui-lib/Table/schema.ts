import { z } from "zod";

export const TableSchema = z.object({
  properties: z.object({
    columns: z.array(
      z.object({
        title: z.string(),
        field: z.string(),
        sortable: z.boolean().optional(),
        filterable: z.boolean().optional(),
        filterOptions: z.array(z.string()).optional(),
        customized: z.any().optional(),
        format: z.enum(["date", "dateTime", "time"]).optional(),
        tooltip: z.boolean().optional(),
      }),
    ),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
