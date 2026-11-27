"use client";

import type { CSSProperties, ReactNode } from "react";
import styles from "../card.module.css";
import { classNames } from "../classNames";
import type { CardProps } from "../schema";

const alignMap = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  stretch: "stretch",
  baseline: "baseline",
} as const;

const justifyMap = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  between: "space-between",
  around: "space-around",
  evenly: "space-evenly",
} as const;

export type CardViewProps = Pick<CardProps, "direction" | "align" | "justify" | "wrap"> & {
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
};

export function CardView({
  children,
  className,
  direction,
  align,
  justify,
  wrap,
  style,
}: CardViewProps) {
  const flexStyle: CSSProperties = {
    flexDirection: direction === "row" ? "row" : "column",
    flexWrap: wrap ? "wrap" : undefined,
    alignItems: align ? alignMap[align] : undefined,
    justifyContent: justify ? justifyMap[justify] : undefined,
    ...style,
  };

  return (
    <div className={classNames(styles["root"], styles["variantCard"], className)} style={flexStyle}>
      {children}
    </div>
  );
}
