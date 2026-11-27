"use client";

import type { CSSProperties } from "react";

export type ButtonViewProps = {
  disabled?: boolean;
  status?: "default" | "primary" | "risk";
  style?: CSSProperties;
  text?: string;
  type?: "default" | "text";
};

export function resolveButtonAppearance(props: Pick<ButtonViewProps, "status" | "type">) {
  return {
    antType: props.type === "text" ? "text" : props.status === "primary" ? "primary" : "default",
    danger: props.status === "risk",
  } as const;
}
