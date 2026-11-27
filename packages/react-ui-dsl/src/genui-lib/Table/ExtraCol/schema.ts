import { z } from "zod";

export const ExtraColSchema = z
  .object({
    title: z.string(),
    value: z.any(),
  })
  .strict();

export type ExtraColProps = z.infer<typeof ExtraColSchema>;
