import { z } from "zod";

export const CardSchema = z
  .object({
    children: z.array(z.any()).optional(),
    variant: z.enum(["card", "clear", "sunk"]).optional(),
    width: z.enum(["standard", "full"]).optional(),
  })
  .strict();
