import { z } from "zod";

export const ImageSchema = z.object({
  src: z.string(),
  alt: z.string().optional(),
});

export type ImageProps = z.infer<typeof ImageSchema>;
