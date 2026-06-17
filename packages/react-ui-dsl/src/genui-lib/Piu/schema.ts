import { z } from "zod";

export const PiuSchema = z.object({
  name: z.string(),
  eventName: z.string().optional(),
  destroy: z.boolean().optional(),
  param: z.record(z.string(), z.unknown()),
});

export type PiuProps = z.infer<typeof PiuSchema>;
