import { z } from "zod";
import { RadioItem } from "../RadioItem";

export const RadioGroupSchema = z.object({
  items: z.array(RadioItem.ref),
  defaultValue: z.string().optional(),
  value: z.string().optional(),
});

export type RadioGroupProps = z.infer<typeof RadioGroupSchema>;
