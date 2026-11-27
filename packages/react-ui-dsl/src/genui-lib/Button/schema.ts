import { z } from "zod";

export const ButtonSchema = z.object({
  text: z.string().optional(),
  status: z.enum(["default", "primary", "risk"]).optional(),
  disabled: z.boolean().optional(),
  type: z.enum(["default", "text"]).optional(),
});
