import { z } from "zod";

export const ImageBlockSchema = z.object({
  src: z.string(),
  alt: z.string().optional(),
});

export type ImageBlockProps = z.infer<typeof ImageBlockSchema>;
