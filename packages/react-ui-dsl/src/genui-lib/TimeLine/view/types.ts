"use client";

import type { CSSProperties, ReactNode } from "react";

export type TimelineItemView = {
  content: ReactNode;
  iconType: "success" | "error" | "default";
  title: string;
};

export type TimeLineViewProps = {
  items: TimelineItemView[];
  style?: CSSProperties;
  title?: string;
};

export const iconColorMap = {
  default: "gray",
  error: "red",
  success: "green",
} as const;
