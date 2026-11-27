import { z } from "zod";
import { SelectItem } from "../SelectItem";

export const SelectSchema = z.object({
  items: z.array(SelectItem.ref).optional(),
  placeholder: z.string().optional(),
  value: z.string().optional(),
});

export type SelectProps = z.infer<typeof SelectSchema>;
