import { z } from "zod";

export const StepsItemSchema = z.object({
  title: z.string(),
  details: z.any().optional(),
});

StepsItemSchema.register(z.globalRegistry, { id: "StepsItem" });

export const StepsSchema = z.object({
  items: z.array(StepsItemSchema),
});

export type StepsItemProps = z.infer<typeof StepsItemSchema>;
export type StepsProps = z.infer<typeof StepsSchema>;
