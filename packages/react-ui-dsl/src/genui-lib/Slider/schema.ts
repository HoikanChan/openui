import { z } from "zod";

export const SliderSchema = z.object({
  variant: z.enum(["continuous", "discrete"]),
  min: z.number(),
  max: z.number(),
  defaultValue: z.array(z.number()).optional(),
  label: z.string().optional(),
});

export type SliderProps = z.infer<typeof SliderSchema>;
