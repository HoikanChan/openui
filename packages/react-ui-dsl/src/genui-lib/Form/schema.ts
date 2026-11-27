import { z } from "zod";
import { Buttons } from "../Buttons";
import { FormControl } from "../FormControl";

export const FormSchema = z.object({
  name: z.string(),
  buttons: Buttons.ref,
  fields: z.array(FormControl.ref).optional(),
  layout: z.enum(["vertical", "inline", "horizontal"]).optional(),
  labelAlign: z.enum(["left", "right"]).optional(),
  initialValues: z.record(z.string(), z.any()).optional(),
});

export type FormProps = z.infer<typeof FormSchema>;
