"use client";

import { Select as AntSelect } from "antd";
import type { SelectViewProps } from "./types";

export function SelectView(props: SelectViewProps) {
  return (
    <AntSelect
      options={props.items}
      placeholder={props.placeholder}
      style={{ width: "100%", ...props.style }}
      value={props.value}
    />
  );
}
