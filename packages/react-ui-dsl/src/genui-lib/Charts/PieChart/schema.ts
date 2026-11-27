import { z } from "zod";

export const PieChartSchema = z.object({
  labels: z.array(z.string()),
  values: z.array(z.number()),
  variant: z.enum(["pie", "donut"]).optional(),
});
