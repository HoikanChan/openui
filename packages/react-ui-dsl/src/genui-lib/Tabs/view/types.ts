"use client";

import type { CSSProperties, ReactNode } from "react";

export type TabViewItem = {
  value: string;
  label: string;
  children: ReactNode;
  loading: boolean;
};

export type TabViewProps = {
  items: TabViewItem[];
  activeTab: string;
  onTabChange: (value: string) => void;
  style?: CSSProperties;
};
