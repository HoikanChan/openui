"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { SimpleTimeLineItemSchema, StructuredTimeLineItemSchema, TimeLineSchema } from "./schema";
import {
  buildTimelineItems as buildTimelineViewItems,
  type TimelineItemView,
  TimeLineView,
} from "./view";

type StructuredTimelineItem = z.infer<typeof StructuredTimeLineItemSchema>;
type SimpleTimelineItem = z.infer<typeof SimpleTimeLineItemSchema>;
type TimelineItemInput = z.infer<typeof TimeLineSchema>["data"][number];

function isStructuredTimelineItem(item: TimelineItemInput): item is StructuredTimelineItem {
  return "content" in item;
}

export function normalizeTimelineItems(
  data: z.infer<typeof TimeLineSchema>["data"],
  renderNode: (value: unknown) => React.ReactNode,
): TimelineItemView[] {
  return data.map((item: TimelineItemInput) => {
    if (isStructuredTimelineItem(item)) {
      return {
        content: renderNode(item.content.children),
        iconType: item.iconType,
        title: item.content.title,
      };
    }

    const simpleItem = item as SimpleTimelineItem;
    return {
      content: simpleItem.description == null ? null : renderNode(simpleItem.description),
      iconType: simpleItem.status ?? "default",
      title: simpleItem.title,
    };
  });
}

export function buildTimelineItems(
  data: z.infer<typeof TimeLineSchema>["data"],
  renderNode: (value: unknown) => React.ReactNode,
) {
  return buildTimelineViewItems(normalizeTimelineItems(data, renderNode));
}

export const TimeLine = defineComponent({
  name: "TimeLine",
  props: TimeLineSchema,
  description: "Timeline that accepts either typed DSL items or raw rows with title/description/status",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TimeLineSchema>>) => (
    <TimeLineView
      items={normalizeTimelineItems(props.data, renderNode)}
      title={props.title}
    />
  ),
});
