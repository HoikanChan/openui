"use client";

import type { CSSProperties } from "react";
import type { ButtonProps } from "../schema";

export type ButtonViewProps = {
  label: string;
  variant?: ButtonProps["variant"];
  type?: ButtonProps["type"];
  size?: ButtonProps["size"];
  disabled?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
};

type ButtonAppearanceInput = Pick<ButtonViewProps, "variant" | "type" | "size">;

const antSizeMap = {
  "extra-small": "small",
  small: "small",
  normal: "middle",
  large: "large",
} as const;

function resolveAntType(props: ButtonAppearanceInput): "primary" | "text" | "default" {
  if (props.type === "text") return "text";
  if (props.variant === "primary") return "primary";
  if (props.variant === "tertiary") return "text";
  return "default";
}

export function resolveButtonAppearance(props: ButtonAppearanceInput) {
  return {
    antType: resolveAntType(props),
    antSize: props.size ? antSizeMap[props.size] : "middle",
  } as const;
}
