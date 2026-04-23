import { z } from "zod";
import { FlexPropsSchema } from "../flexPropsSchema";

export const HLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  gap: FlexPropsSchema.shape.gap,
  wrap: FlexPropsSchema.shape.wrap,
  align: FlexPropsSchema.shape.align,
  justify: FlexPropsSchema.shape.justify,
});
