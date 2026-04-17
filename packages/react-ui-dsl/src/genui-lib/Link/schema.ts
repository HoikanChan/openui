import { z } from "zod";

export const LinkSchema = z.object({
  properties: z.object({
    href: z.string(),
    text: z.string().optional(),
    target: z.enum(["_self", "_blank"]).optional(),
    disabled: z.boolean().optional(),
    download: z.string().optional(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
