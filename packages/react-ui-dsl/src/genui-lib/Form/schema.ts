import { z } from "zod";

export const FormSchema = z.object({
  fields: z.array(
    z.object({
      label: z.string(),
      name: z.string(),
      rules: z.array(z.object({ required: z.boolean() })).optional(),
      component: z.any(),
    }),
  ),
  layout: z.enum(["vertical", "inline", "horizontal"]).optional(),
  labelAlign: z.enum(["left", "right"]).optional(),
  initialValues: z.record(z.string(), z.any()).optional(),
});
