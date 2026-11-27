"use client";

import { Input as AntInput } from "antd";
import type { InputViewProps } from "./types";

export function InputView({ placeholder, style, type = "text", value }: InputViewProps) {
  const mergedStyle = { width: "100%", ...style };

  switch (type) {
    case "password":
      return (
        <AntInput.Password placeholder={placeholder} style={mergedStyle} value={value} />
      );
    case "email":
    case "number":
    case "url":
      return <AntInput placeholder={placeholder} style={mergedStyle} type={type} value={value} />;
    case "text":
      return (
        <AntInput placeholder={placeholder} style={mergedStyle} type="text" value={value} />
      );
  }
}
