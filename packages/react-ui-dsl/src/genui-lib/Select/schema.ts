import { z } from "zod";

export const SelectSchema = z.object({
  properties: z.object({
    options: z.array(
      z.object({
        label: z.string(),
        value: z.union([z.number(), z.string()]),
      }),
    ),
    allowClear: z.boolean().optional(),
    defaultValue: z.union([z.number(), z.string()]).optional(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
