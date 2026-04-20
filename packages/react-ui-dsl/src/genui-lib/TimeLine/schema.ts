import { z } from "zod";

export const TimeLineSchema = z.object({
  data: z.array(
    z.object({
      content: z.object({
        title: z.string(),
        children: z.array(z.any()),
      }),
      iconType: z.enum(["success", "error", "default"]),
    }),
  ),
  title: z.string().optional(),
  id: z.string().optional(),
});
