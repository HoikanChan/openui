"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TimeLineSchema } from "./schema";
import {
  buildTimelineItems as buildTimelineViewItems,
  TimeLineView,
} from "./view";

export function buildTimelineItems(
  data: z.infer<typeof TimeLineSchema>["data"],
  renderNode: (value: unknown) => React.ReactNode,
) {
  return buildTimelineViewItems(
    data.map((item) => ({
      content: renderNode(item.content.children),
      iconType: item.iconType,
      title: item.content.title,
    })),
  );
}

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
      title={props.title}
    />
  ),
});
