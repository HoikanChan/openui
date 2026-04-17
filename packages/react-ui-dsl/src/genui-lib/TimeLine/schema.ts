import { z } from "zod";

export const TimeLineSchema = z.object({
  properties: z
    .object({
      title: z.string().optional(),
      id: z.string().optional(),
    })
    .optional(),
  data: z.array(
    z.object({
      content: z.object({
        title: z.string(),
        children: z.array(z.any()),
      }),
      iconType: z.enum(["success", "error", "default"]),
    }),
  ),
  style: z.record(z.string(), z.any()).optional(),
});
