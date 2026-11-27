import { z } from "zod";

export const TabItemSchema = z.object({
  value: z.string(),
  label: z.string(),
  content: z.array(z.any()),
});

TabItemSchema.register(z.globalRegistry, { id: "TabItem" });

export type TabItemProps = z.infer<typeof TabItemSchema>;
