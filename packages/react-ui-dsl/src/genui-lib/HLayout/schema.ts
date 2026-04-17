import { z } from "zod";

export const HLayoutSchema = z.object({
  properties: z.object({
    gap: z.number().optional(),
    wrap: z.boolean().optional(),
  }),
  children: z.array(z.any()),
  style: z.record(z.string(), z.any()).optional(),
});
