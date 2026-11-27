"use client";

import { Timeline } from "antd";
import { iconColorMap, type TimelineItemView, type TimeLineViewProps } from "./types";

export function buildTimelineItems(items: TimelineItemView[]) {
  return items.map((item) => ({
    children: (
      <>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>{item.title}</div>
        {item.content}
      </>
    ),
    color: iconColorMap[item.iconType],
  }));
}

export function TimeLineView({ items, style, title }: TimeLineViewProps) {
  return (
    <div style={style}>
      {title && <div style={{ fontWeight: 700, marginBottom: 12 }}>{title}</div>}
      <Timeline items={buildTimelineItems(items)} />
    </div>
  );
}
