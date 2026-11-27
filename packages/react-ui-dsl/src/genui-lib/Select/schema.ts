import { z } from "zod";

/** Mirrors the contract SelectItem shape; SelectItem registration is owned elsewhere. */
const SelectItemOptionSchema = z.object({
  label: z.string(),
  value: z.string(),
});

export const SelectSchema = z.object({
  items: z.array(SelectItemOptionSchema).optional(),
  placeholder: z.string().optional(),
  value: z.string().optional(),
});

export type SelectProps = z.infer<typeof SelectSchema>;
