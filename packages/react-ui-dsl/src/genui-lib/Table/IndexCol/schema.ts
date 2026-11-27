import { z } from "zod";

export const IndexColSchema = z
  .object({
    title: z.string(),
  })
  .strict();

export type IndexColProps = z.infer<typeof IndexColSchema>;
