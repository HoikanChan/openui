import { z } from "zod";

export const PreviewCardSchema = z
  .object({
    children: z.array(z.any()),
    title: z.string(),
    summary: z.array(z.any()).optional(),
    type: z.enum(["replace", "append"]).default("append"),
    tabId: z.string().optional(),
    cardId: z.string().optional(),
  })
  .strict();

export type PreviewCardProps = z.infer<typeof PreviewCardSchema>;
