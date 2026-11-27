import { z } from "zod";

export const InputSchema = z.object({
  placeholder: z.string().optional(),
  defaultValue: z.union([z.string(), z.number()]).optional(),
  disabled: z.boolean().optional(),
  readOnly: z.boolean().optional(),
  size: z.enum(["small", "medium", "large"]).optional(),
  hasError: z.boolean().optional(),
  maxLength: z.number().optional(),
});
