"use client";

import { Typography } from "antd";
import type { CSSProperties } from "react";

export type LinkViewProps = {
  disabled?: boolean;
  download?: string;
  href: string;
  style?: CSSProperties;
  target?: "_self" | "_blank";
  text?: string;
};

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
