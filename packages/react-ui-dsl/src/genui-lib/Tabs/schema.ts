import { z } from "zod";

export const TabItemSchema = z.object({
  value: z.string(),
  label: z.string(),
  content: z.array(z.any()),
});

export const TabsSchema = z.object({
  items: z.array(TabItemSchema),
  style: z.record(z.string(), z.any()).optional(),
});
