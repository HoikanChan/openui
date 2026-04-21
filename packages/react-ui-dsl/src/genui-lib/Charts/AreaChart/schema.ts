import { z } from "zod";
import { SeriesSchema } from "../Series";

export const AreaChartSchema = z.object({
  labels: z.array(z.string()),
  series: z.array(SeriesSchema),
  variant: z.enum(["linear", "smooth", "step"]).optional(),
  xLabel: z.string().optional(),
  yLabel: z.string().optional(),
});
