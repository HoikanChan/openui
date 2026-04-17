import { z } from "zod";

export const VLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  properties: z
    .object({
      gap: z.number().optional(),
    })
    .optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
});
