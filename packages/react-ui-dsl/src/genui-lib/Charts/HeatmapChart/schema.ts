import { z } from "zod";

export const HeatmapChartSchema = z.object({
  xLabels: z.array(z.string()),
  yLabels: z.array(z.string()),
  values: z.array(z.array(z.number())),
});
