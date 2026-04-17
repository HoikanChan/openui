"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Select as AntSelect } from "antd";
import { SelectSchema } from "./schema";

export const Select = defineComponent({
  name: "Select",
  props: SelectSchema,
  description: "Dropdown select",
  component: ({ props }) => {
    const { options, allowClear, defaultValue } = props.properties;
    return (
      <AntSelect
        options={options}
        allowClear={allowClear}
        defaultValue={defaultValue}
        style={{ width: "100%", ...(props.style as React.CSSProperties) }}
      />
    );
  },
});
