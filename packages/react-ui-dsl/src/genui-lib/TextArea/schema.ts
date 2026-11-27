import { z } from "zod";

export const TextAreaSchema = z.object({
  rows: z.number().optional(),
  placeholder: z.string().optional(),
  value: z.string().optional(),
});

export type TextAreaProps = z.infer<typeof TextAreaSchema>;
