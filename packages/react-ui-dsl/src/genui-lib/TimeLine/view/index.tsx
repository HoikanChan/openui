"use client";

import { Timeline } from "antd";
import type { CSSProperties, ReactNode } from "react";

const iconColorMap = {
  default: "gray",
  error: "red",
  success: "green",
} as const;

export type TimelineItemView = {
  content: ReactNode;
  iconType: "success" | "error" | "default";
  title: string;
};

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

export type TimeLineViewProps = {
  items: TimelineItemView[];
  style?: CSSProperties;
  title?: string;
};

export function TimeLineView({ items, style, title }: TimeLineViewProps) {
  return (
    <div style={style}>
      {title && <div style={{ fontWeight: 700, marginBottom: 12 }}>{title}</div>}
      <Timeline items={buildTimelineItems(items)} />
    </div>
  );
}
