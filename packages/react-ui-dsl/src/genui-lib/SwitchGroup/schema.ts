import { z } from "zod";
import { SwitchItem } from "../SwitchItem";

export const SwitchGroupSchema = z.object({
  items: z.array(SwitchItem.ref),
  variant: z.enum(["clear", "card", "sunk"]).optional(),
  value: z.record(z.string(), z.boolean()).optional(),
});

export type SwitchGroupProps = z.infer<typeof SwitchGroupSchema>;
