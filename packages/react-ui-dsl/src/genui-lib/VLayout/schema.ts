import { z } from "zod";

export const VLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  gap: z.number().optional(),
});
