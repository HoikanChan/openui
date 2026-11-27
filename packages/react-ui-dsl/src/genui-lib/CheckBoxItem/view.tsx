"use client";

import { Checkbox, Typography } from "antd";

export type CheckBoxItemViewProps = {
  name?: string;
  label: string;
  description?: string;
  defaultChecked?: boolean;
};

export function CheckBoxItemView({
  name,
  label,
  description,
  defaultChecked,
}: CheckBoxItemViewProps) {
  return (
    <Checkbox data-checkbox-item={name} defaultChecked={defaultChecked}>
      <span>{label}</span>
      {description ? (
        <Typography.Text style={{ display: "block", fontSize: 12 }} type="secondary">
          {description}
        </Typography.Text>
      ) : null}
    </Checkbox>
  );
}
