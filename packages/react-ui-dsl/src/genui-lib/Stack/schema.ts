import { z } from "zod";
import { FlexPropsSchema } from "../flexPropsSchema";

export const StackSchema = z.object({
  children: z.array(z.any()).optional(),
  direction: FlexPropsSchema.shape.direction,
  gap: FlexPropsSchema.shape.gap,
  align: FlexPropsSchema.shape.align,
  justify: FlexPropsSchema.shape.justify,
  wrap: FlexPropsSchema.shape.wrap,
});
