import { z } from "zod";

export const TextSchema = z.object({
  properties: z.object({
    type: z.enum(["default", "markdown", "html"]).optional(),
    content: z.string(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
