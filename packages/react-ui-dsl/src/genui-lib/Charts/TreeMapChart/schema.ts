import { z } from "zod";

export const TreeMapChartSchema = z.object({
  data: z.array(z.object({
    name: z.string(),
    value: z.number(),
    group: z.string().optional(),
  })),
});
