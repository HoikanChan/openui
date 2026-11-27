"use client";

import { Input as AntInput } from "antd";
import type { InputSize, InputViewProps } from "./types";

type AntInputSize = "small" | "middle" | "large";

function mapSize(size: InputSize | undefined): AntInputSize | undefined {
  if (size === "medium") return "middle";
  return size;
}

export function InputView(props: InputViewProps) {
  const size = mapSize(props.size);
  const status = props.hasError ? "error" : undefined;
  const style = { width: "100%", ...props.style };

  return (
    <AntInput
      defaultValue={props.defaultValue}
      disabled={props.disabled}
      maxLength={props.maxLength}
      placeholder={props.placeholder}
      readOnly={props.readOnly}
      size={size}
      status={status}
      style={style}
    />
  );
}
