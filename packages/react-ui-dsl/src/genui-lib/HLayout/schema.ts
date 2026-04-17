import { z } from "zod";

export const HLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  properties: z
    .object({
      gap: z.number().optional(),
      wrap: z.boolean().optional(),
    })
    .optional(),
  style: z.record(z.string(), z.any()).optional(),
});
