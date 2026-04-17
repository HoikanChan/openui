import { z } from "zod";

export const ListSchema = z.object({
  properties: z.object({
    header: z.string().optional(),
    isOrder: z.boolean().optional(),
  }).optional(),
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
