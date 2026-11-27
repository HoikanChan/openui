import { z } from "zod";

export const HTMLLoaderSchema = z
  .object({
    url: z.string(),
    iframeId: z.string(),
    data: z.record(z.string(), z.unknown()),
  })
  .strict();

export type HTMLLoaderProps = z.infer<typeof HTMLLoaderSchema>;