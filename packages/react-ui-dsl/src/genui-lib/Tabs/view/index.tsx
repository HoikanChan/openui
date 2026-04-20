"use client";

import { Skeleton, Tabs } from "antd";
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

export function TabView({ items, activeTab, onTabChange, style }: TabViewProps) {
  return (
    <div style={style}>
      <Tabs
        activeKey={activeTab}
        onChange={onTabChange}
        items={items.map((item) => ({
          key: item.value,
          label: item.label,
          children: item.loading ? (
            <Skeleton active paragraph={{ rows: 3 }} />
          ) : (
            item.children
          ),
        }))}
      />
    </div>
  );
}
