import { z } from "zod";

export const CardSchema = z.object({
  properties: z.object({
    tag: z.string().optional(),
    header: z.string().optional(),
    headerAlign: z.enum(["left", "center", "right"]).optional(),
  }).optional(),
  children: z.array(z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
