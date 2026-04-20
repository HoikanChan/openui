"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TimeLineSchema } from "./schema";
import { TimeLineView } from "./view";

export const TimeLine = defineComponent({
  name: "TimeLine",
  props: TimeLineSchema,
  description: "Timeline with typed items, each containing a DSL children tree",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TimeLineSchema>>) => (
    <TimeLineView
      items={props.data.map((item: z.infer<typeof TimeLineSchema>["data"][number]) => ({
        content: renderNode(item.content.children),
        iconType: item.iconType,
        title: item.content.title,
      }))}
      style={props.style as React.CSSProperties}
      title={props.properties?.title}
    />
  ),
});
