import { z } from "zod";

export const VLayoutSchema = z.object({
  properties: z
    .object({
      gap: z.number().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
});
