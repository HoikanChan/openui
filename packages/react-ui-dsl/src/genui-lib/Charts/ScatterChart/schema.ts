import { z } from "zod";
import { ScatterSeriesSchema } from "../ScatterSeries";

export const ScatterChartSchema = z.object({
  datasets: z.array(ScatterSeriesSchema),
  xLabel: z.string().optional(),
  yLabel: z.string().optional(),
});
