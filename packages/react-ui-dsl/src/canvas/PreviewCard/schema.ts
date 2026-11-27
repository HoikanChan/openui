import { z } from "zod";

export const PreviewCardSchema = z
  .object({
    children: z.array(z.any()),
    title: z.string(),
    summary: z.array(z.any()).optional(),
  })
  .strict();

export type PreviewCardProps = z.infer<typeof PreviewCardSchema>;