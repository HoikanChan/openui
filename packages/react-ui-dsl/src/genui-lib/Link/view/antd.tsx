"use client";

import { Typography } from "antd";
import type { LinkViewProps } from "./types";

export function LinkView({ disabled, download, href, style, target, text }: LinkViewProps) {
  return (
    <Typography.Link
      disabled={disabled}
      download={download}
      href={disabled ? undefined : href}
      style={style}
      target={target}
    >
      {text ?? href}
    </Typography.Link>
  );
}
