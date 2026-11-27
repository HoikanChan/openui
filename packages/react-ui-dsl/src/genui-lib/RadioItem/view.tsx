"use client";

import { Radio, Typography } from "antd";

export type RadioItemViewProps = {
  label: string;
  description?: string;
  value: string;
};

export function RadioItemView({ label, description, value }: RadioItemViewProps) {
  return (
    <Radio value={value}>
      <span>{label}</span>
      {description ? (
        <Typography.Text style={{ display: "block", fontSize: 12 }} type="secondary">
          {description}
        </Typography.Text>
      ) : null}
    </Radio>
  );
}
