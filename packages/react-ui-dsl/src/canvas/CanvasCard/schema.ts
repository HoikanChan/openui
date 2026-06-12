import { z } from "zod";

export const CanvasCardSchema = z
  .object({
    children: z.array(z.any()),
    title: z.string().optional(),
    tab: z.string().optional().default("Dashboard"),
    size: z
      .object({
        w: z.number().int().min(1).max(12).optional().default(6),
      })
      .optional()
      .default({ w: 6 }),
  })
  .strict();

export type CanvasCardProps = z.infer<typeof CanvasCardSchema>;