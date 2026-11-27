import { z } from "zod";

export const DatePickerSchema = z.object({
  mode: z.enum(["single", "range"]).optional(),
});

export type DatePickerProps = z.infer<typeof DatePickerSchema>;
