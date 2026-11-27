import { z } from "zod";

export const TagSchema = z.object({
  text: z.string(),
  variant: z.enum(["neutral", "info", "success", "warning", "danger"]).optional(),
  icon: z.string().optional(),
  size: z.enum(["sm", "md", "lg"]).optional(),
});
