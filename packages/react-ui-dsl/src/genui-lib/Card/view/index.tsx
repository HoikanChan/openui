"use client";

import type { CSSProperties, ReactNode } from "react";
import { classNames } from "../classNames";
import styles from "../card.module.css";

const variantClassMap = {
  card: styles["variantCard"],
  clear: styles["variantClear"],
  sunk: styles["variantSunk"],
} as const;

export type CardViewProps = {
  children?: ReactNode;
  className?: string;
  style?: CSSProperties;
  variant?: keyof typeof variantClassMap;
};

export function CardView({ children, className, style, variant = "card" }: CardViewProps) {
  return (
    <div
      className={classNames(styles["root"], variantClassMap[variant], className)}
      style={style}
    >
      {children}
    </div>
  );
}
