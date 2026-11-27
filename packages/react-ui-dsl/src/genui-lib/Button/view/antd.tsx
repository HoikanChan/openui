"use client";

import { Button as AntButton } from "antd";
import { resolveButtonAppearance, type ButtonViewProps } from "./types";

export { resolveButtonAppearance };

export function ButtonView(props: ButtonViewProps) {
  const appearance = resolveButtonAppearance(props);

  return (
    <AntButton
      disabled={props.disabled}
      onClick={props.onClick}
      size={appearance.antSize}
      style={props.style}
      type={appearance.antType}
    >
      {props.label}
    </AntButton>
  );
}
