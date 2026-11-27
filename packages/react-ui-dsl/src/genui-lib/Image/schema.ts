import { z } from "zod";

export const ImageSchema = z.object({
  content: z.string(),
  type: z.enum(["url", "base64", "svg"]),
});
