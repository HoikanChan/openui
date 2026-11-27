"use client";

import { Tag as AntTag } from "antd";
import { resolveTagAppearance, type TagViewProps } from "./types";

export { resolveTagAppearance };

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
