"use client";

import { Button as AntButton } from "antd";
import { resolveButtonAppearance, type ButtonViewProps } from "./types";

export { resolveButtonAppearance };

export function ButtonView(props: ButtonViewProps) {
  const appearance = resolveButtonAppearance(props);

  return (
    <AntButton
      danger={appearance.danger}
      disabled={props.disabled}
      style={props.style}
      type={appearance.antType}
    >
      {props.text}
    </AntButton>
  );
}
