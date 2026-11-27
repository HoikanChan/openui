import { z } from "zod";
import { TabItem } from "../TabItem";

export const TabsSchema = z.object({
  items: z.array(TabItem.ref),
});

export type TabsProps = z.infer<typeof TabsSchema>;
