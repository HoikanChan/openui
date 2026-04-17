import { z } from "zod";

export const ImageSchema = z.object({
  properties: z.object({
    type: z.enum(["url", "base64", "svg"]),
    content: z.string(),
  }),
  style: z.record(z.string(), z.any()).optional(),
});
