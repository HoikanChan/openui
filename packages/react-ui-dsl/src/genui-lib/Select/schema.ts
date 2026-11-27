import { z } from "zod";

export const SelectSchema = z.object({
  options: z.array(
    z.object({
      label: z.string(),
      value: z.union([z.number(), z.string()]),
    }),
  ),
  defaultValue: z.union([z.number(), z.string()]).optional(),
  allowClear: z.boolean().optional(),
});
