import { z } from "zod";
import { CheckBoxItem } from "../CheckBoxItem";

export const CheckBoxGroupSchema = z.object({
  items: z.array(CheckBoxItem.ref),
  value: z.record(z.string(), z.boolean()).optional(),
});

export type CheckBoxGroupProps = z.infer<typeof CheckBoxGroupSchema>;
