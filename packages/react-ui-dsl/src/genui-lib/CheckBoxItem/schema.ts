import { z } from "zod";

export const CheckBoxItemSchema = z.object({
  name: z.string(),
  label: z.string(),
  description: z.string().optional(),
  defaultChecked: z.boolean().optional(),
});

export type CheckBoxItemProps = z.infer<typeof CheckBoxItemSchema>;
