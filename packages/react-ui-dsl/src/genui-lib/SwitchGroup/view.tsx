"use client";

import type { CSSProperties } from "react";
import type { SwitchItemProps } from "../SwitchItem/schema";
import { SwitchItemView } from "../SwitchItem/view";

export type SwitchGroupVariant = "clear" | "card" | "sunk";

export type SwitchGroupViewProps = {
  items: SwitchItemProps[];
  variant?: SwitchGroupVariant;
  value?: Record<string, boolean>;
};

const variantStyles: Record<SwitchGroupVariant, CSSProperties> = {
  clear: {},
  card: { background: "#ffffff", border: "1px solid #f0f0f0", borderRadius: 8, padding: 12 },
  sunk: { background: "#f5f5f5", borderRadius: 8, padding: 12 },
};

export function SwitchGroupView({ items, variant = "clear", value }: SwitchGroupViewProps) {
  return (
    <div
      data-switch-group=""
      style={{ display: "flex", flexDirection: "column", gap: 8, ...variantStyles[variant] }}
    >
      {items.map((item) => (
        <SwitchItemView
          key={item.name}
          defaultChecked={value?.[item.name] ?? item.defaultChecked}
          label={item.label}
          name={item.name}
        />
      ))}
    </div>
  );
}
