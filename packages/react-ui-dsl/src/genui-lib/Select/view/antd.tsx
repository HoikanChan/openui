"use client";

import { Select as AntSelect } from "antd";
import type { SelectViewProps } from "./types";

export function SelectView(props: SelectViewProps) {
  return (
    <AntSelect
      allowClear={props.allowClear}
      defaultValue={props.defaultValue}
      options={props.options}
      style={{ width: "100%", ...props.style }}
    />
  );
}
