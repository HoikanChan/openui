import { z } from "zod";

export const CardSchema = z
  .object({
    children: z.array(z.any()).optional(),
    direction: z.enum(["row", "column"]).optional(),
    align: z.enum(["start", "center", "end", "stretch", "baseline"]).optional(),
    justify: z.enum(["start", "center", "end", "between", "around", "evenly"]).optional(),
    wrap: z.boolean().optional(),
  })
  .strict();

export type CardProps = z.infer<typeof CardSchema>;
