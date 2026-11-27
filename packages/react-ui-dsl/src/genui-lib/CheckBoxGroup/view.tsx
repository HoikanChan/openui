"use client";

import { Flex } from "antd";
import type { CheckBoxItemProps } from "../CheckBoxItem/schema";
import { CheckBoxItemView } from "../CheckBoxItem/view";

export type CheckBoxGroupViewProps = {
  items: CheckBoxItemProps[];
  value?: Record<string, boolean>;
};

export function CheckBoxGroupView({ items, value }: CheckBoxGroupViewProps) {
  return (
    <Flex data-checkbox-group="" gap="small" vertical>
      {items.map((item) => (
        <CheckBoxItemView
          key={item.name}
          defaultChecked={value?.[item.name] ?? item.defaultChecked}
          description={item.description}
          label={item.label}
          name={item.name}
        />
      ))}
    </Flex>
  );
}
