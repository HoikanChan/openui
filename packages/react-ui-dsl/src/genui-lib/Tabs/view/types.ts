"use client";

import type { ReactNode } from "react";

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
};
