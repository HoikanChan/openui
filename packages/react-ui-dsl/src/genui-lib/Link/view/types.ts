"use client";

import type { CSSProperties } from "react";

export type LinkViewProps = {
  disabled?: boolean;
  download?: string;
  href: string;
  style?: CSSProperties;
  target?: "_self" | "_blank";
  text?: string;
};
