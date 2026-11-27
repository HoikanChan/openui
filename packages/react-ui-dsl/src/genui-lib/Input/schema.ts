import { z } from "zod";

export const InputSchema = z.object({
  placeholder: z.string().optional(),
  type: z.enum(["text", "email", "password", "number", "url"]).optional(),
  value: z.string().optional(),
});

export type InputProps = z.infer<typeof InputSchema>;
