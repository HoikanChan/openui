"use client";

import { Radio } from "antd";
import type { RadioItemProps } from "../RadioItem/schema";
import { RadioItemView } from "../RadioItem/view";

export type RadioGroupViewProps = {
  items: RadioItemProps[];
  defaultValue?: string;
  value?: string;
};

export function RadioGroupView({ items, defaultValue, value }: RadioGroupViewProps) {
  return (
    <Radio.Group
      data-radio-group=""
      defaultValue={value ?? defaultValue}
      style={{ display: "flex", flexDirection: "column", gap: 8 }}
    >
      {items.map((item) => (
        <RadioItemView
          key={item.value}
          description={item.description}
          label={item.label}
          value={item.value}
        />
      ))}
    </Radio.Group>
  );
}
