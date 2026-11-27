"use client";

import type { ReactNode } from "react";
import type { TabItemProps } from "../schema";

export type TabItemViewProps = {
  value: TabItemProps["value"];
  label: TabItemProps["label"];
  children?: ReactNode;
};

export function TabItemView({ children, label, value }: TabItemViewProps) {
  return (
    <div aria-label={label} data-tab-value={value} role="tabpanel">
      {children}
    </div>
  );
}
