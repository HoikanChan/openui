import { z } from "zod";
import { CheckBoxGroup } from "../CheckBoxGroup";
import { DatePicker } from "../DatePicker";
import { Input } from "../Input";
import { RadioGroup } from "../RadioGroup";
import { Select } from "../Select";
import { Slider } from "../Slider";
import { TextArea } from "../TextArea";

export const FormControlRulesSchema = z.object({
  required: z.boolean().optional(),
  email: z.boolean().optional(),
  url: z.boolean().optional(),
  numeric: z.boolean().optional(),
  min: z.number().optional(),
  max: z.number().optional(),
  minLength: z.number().optional(),
  maxLength: z.number().optional(),
  pattern: z.string().optional(),
});

export const FormControlSchema = z.object({
  name: z.string(),
  label: z.string(),
  input: z.union([
    Input.ref,
    TextArea.ref,
    Select.ref,
    Slider.ref,
    CheckBoxGroup.ref,
    RadioGroup.ref,
    DatePicker.ref,
  ]),
  rules: FormControlRulesSchema.optional(),
  hint: z.string().optional(),
});

export type FormControlRules = z.infer<typeof FormControlRulesSchema>;
export type FormControlProps = z.infer<typeof FormControlSchema>;
