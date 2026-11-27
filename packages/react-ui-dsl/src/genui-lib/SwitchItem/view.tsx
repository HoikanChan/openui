"use client";

import { Switch } from "antd";

export type SwitchItemViewProps = {
  name?: string;
  label?: string;
  defaultChecked?: boolean;
};

export function SwitchItemView({ name, label, defaultChecked }: SwitchItemViewProps) {
  return (
    <div
      data-switch-item={name}
      style={{ alignItems: "center", display: "flex", gap: 12, justifyContent: "space-between" }}
    >
      <span>{label}</span>
      <Switch defaultChecked={defaultChecked} size="small" />
    </div>
  );
}
