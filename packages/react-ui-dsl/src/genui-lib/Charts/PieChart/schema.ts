import { z } from "zod";

export const PieChartSchema = z.object({
  labels: z.array(z.string()),
  values: z.array(z.union([z.number(), z.string()])),
  variant: z.enum(["pie", "donut"]).optional(),
  isPercentage: z.boolean().optional(),
});
