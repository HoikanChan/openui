import { z } from "zod";

export const LinkSchema = z.object({
  href: z.string(),
  text: z.string().optional(),
  target: z.enum(["_self", "_blank"]).optional(),
  disabled: z.boolean().optional(),
  download: z.string().optional(),
});
