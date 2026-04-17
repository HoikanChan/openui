import { z } from "zod";

export const HLayoutSchema = z.object({
  properties: z
    .object({
      gap: z.number().optional(),
      wrap: z.boolean().optional(),
    })
    .optional(),
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
