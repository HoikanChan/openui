import { z } from "zod";

export const ListSchema = z.object({
  children: z.array(z.any()).optional(),
  header: z.string().optional(),
  isOrder: z.boolean().optional(),
});
