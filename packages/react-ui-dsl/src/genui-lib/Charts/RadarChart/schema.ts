import { z } from "zod";
import { SeriesSchema } from "../Series";

export const RadarChartSchema = z.object({
  labels: z.array(z.string()),
  series: z.array(SeriesSchema),
});
