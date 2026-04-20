import { z } from "zod";

export const HLayoutSchema = z.object({
  children: z.array(z.any()).optional(),
  gap: z.number().optional(),
  wrap: z.boolean().optional(),
});
