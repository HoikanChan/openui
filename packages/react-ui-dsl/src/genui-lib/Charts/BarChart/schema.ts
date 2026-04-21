import { z } from "zod";
import { SeriesSchema } from "../Series";

export const BarChartSchema = z.object({
  labels: z.array(z.string()),
  series: z.array(SeriesSchema),
  variant: z.enum(["grouped", "stacked"]).optional(),
  xLabel: z.string().optional(),
  yLabel: z.string().optional(),
});
