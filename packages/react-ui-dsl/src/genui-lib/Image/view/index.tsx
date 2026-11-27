"use client";

import type { CSSProperties } from "react";
import type { ImageProps } from "../schema";

export type ImageViewProps = Pick<ImageProps, "src" | "alt"> & {
  style?: CSSProperties;
};

export function ImageView({ src, alt, style }: ImageViewProps) {
  return <img alt={alt ?? ""} src={src} style={{ maxWidth: "100%", ...style }} />;
}
