import { z } from "zod";

export const GaugeChartSchema = z.object({
  readings: z.array(z.object({ name: z.string(), value: z.number() })),
  min: z.number().optional(),
  max: z.number().optional(),
});
