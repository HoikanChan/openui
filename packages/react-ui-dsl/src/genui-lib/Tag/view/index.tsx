"use client";

import { Tag as AntTag } from "antd";
import type { CSSProperties } from "react";

export type TagViewProps = {
  icon?: string;
  size?: "sm" | "md" | "lg";
  style?: CSSProperties;
  text: string;
  variant?: "neutral" | "info" | "success" | "warning" | "danger";
};

const sizeStyleMap: Record<NonNullable<TagViewProps["size"]>, CSSProperties> = {
  sm: { fontSize: 12, lineHeight: "18px", paddingInline: 7, paddingBlock: 0 },
  md: { fontSize: 14, lineHeight: "22px", paddingInline: 11, paddingBlock: 1 },
  lg: { fontSize: 16, lineHeight: "26px", paddingInline: 13, paddingBlock: 2 },
};

export function resolveTagAppearance(props: Pick<TagViewProps, "size" | "variant">) {
  return {
    color:
      props.variant === "info"
        ? "processing"
        : props.variant === "success"
          ? "success"
          : props.variant === "warning"
            ? "warning"
            : props.variant === "danger"
              ? "error"
              : "default",
    style: sizeStyleMap[props.size ?? "md"],
  } as const;
}

export function TagView({ icon, size, style, text, variant }: TagViewProps) {
  const appearance = resolveTagAppearance({ size, variant });

  return (
    <AntTag
      style={{ ...appearance.style, ...style }}
      color={appearance.color}
      data-icon-token={icon}
    >
      {text}
    </AntTag>
  );
}
