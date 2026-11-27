"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { TabItemSchema } from "./schema";
import { TabItemView } from "./view";

export * from "./schema";
export * from "./view";

export const TabItem = defineComponent({
  name: "TabItem",
  props: TabItemSchema,
  description: "value is unique id, trigger is tab label, content is array of components",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TabItemSchema>>) => (
    <TabItemView label={props.label} value={props.value}>
      {renderNode(props.content)}
    </TabItemView>
  ),
});
