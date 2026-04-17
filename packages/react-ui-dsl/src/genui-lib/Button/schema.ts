import { z } from "zod";

export const ButtonSchema = z.object({
  properties: z
    .object({
      status: z.enum(["default", "primary", "risk"]).optional(),
      disabled: z.boolean().optional(),
      text: z.string().optional(),
      type: z.enum(["default", "text"]).optional(),
    })
    .optional(),
  style: z.record(z.string(), z.any()).optional(),
  actions: z.array(z.any()).optional(),
});
