import { z } from "zod";
import { FlexPropsSchema } from "../flexPropsSchema";

export const VLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  gap: FlexPropsSchema.shape.gap,
  align: FlexPropsSchema.shape.align,
  justify: FlexPropsSchema.shape.justify,
  wrap: FlexPropsSchema.shape.wrap,
});
