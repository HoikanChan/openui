import { z } from "zod";

export const TextSchema = z.object({
  content: z.string(),
  type: z.enum(["default", "markdown", "html"]).optional(),
});
