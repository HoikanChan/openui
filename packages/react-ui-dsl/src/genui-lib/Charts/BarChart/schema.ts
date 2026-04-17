import { z } from "zod";

export const BarChartSchema = z.object({
  properties: z.record(z.string(), z.any()).optional(),
  style: z.record(z.string(), z.any()).optional(),
});
