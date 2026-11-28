import { z } from "zod";

export const TextContentSchema = z.object({
  text: z.union([z.string(), z.number()]),
  size: z.enum(["small", "default", "large", "small-heavy", "large-heavy"]).optional(),
});
