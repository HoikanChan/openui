"use client";

import { Skeleton } from "antd";
import { useState } from "react";
import type { ImageBlockProps } from "../schema";

export type ImageBlockViewProps = ImageBlockProps;

export function ImageBlockView({ alt, src }: ImageBlockViewProps) {
  const [loaded, setLoaded] = useState(false);

  return (
    <div style={{ position: "relative", width: "100%" }}>
      {loaded ? null : <Skeleton.Image active style={{ height: 180, width: "100%" }} />}
      <img
        alt={alt ?? ""}
        onError={() => setLoaded(true)}
        onLoad={() => setLoaded(true)}
        src={src}
        style={{ display: loaded ? "block" : "none", maxWidth: "100%" }}
      />
    </div>
  );
}
