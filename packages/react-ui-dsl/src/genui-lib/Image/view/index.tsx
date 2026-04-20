"use client";

import type { CSSProperties } from "react";

export type ImageViewProps = {
  content: string;
  style?: CSSProperties;
  type: "url" | "base64" | "svg";
};

export function ImageView({ content, style, type }: ImageViewProps) {
  if (type === "svg") {
    return <div dangerouslySetInnerHTML={{ __html: content }} style={style} />;
  }

  const src = type === "base64" ? `data:image/*;base64,${content}` : content;
  return <img alt="" src={src} style={{ maxWidth: "100%", ...style }} />;
}
