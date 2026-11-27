import { z } from "zod";

export const SelectItemSchema = z.object({
  label: z.string(),
  value: z.string(),
});

export type SelectItemProps = z.infer<typeof SelectItemSchema>;
