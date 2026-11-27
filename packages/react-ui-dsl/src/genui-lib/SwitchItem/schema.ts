import { z } from "zod";

export const SwitchItemSchema = z.object({
  name: z.string(),
  label: z.string().optional(),
  defaultChecked: z.boolean().optional(),
});

export type SwitchItemProps = z.infer<typeof SwitchItemSchema>;
