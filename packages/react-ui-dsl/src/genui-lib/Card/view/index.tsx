"use client";

import type { ReactNode } from "react";
import { classNames } from "../classNames";
import styles from "../card.module.css";

const variantClassMap = {
  card: styles["variantCard"],
  clear: styles["variantClear"],
  sunk: styles["variantSunk"],
} as const;

const widthClassMap = {
  standard: styles["widthStandard"],
  full: styles["widthFull"],
} as const;

export type CardViewProps = {
  children?: ReactNode;
  className?: string;
  variant?: keyof typeof variantClassMap;
  width?: keyof typeof widthClassMap;
};

export function CardView({ children, className, variant = "card", width = "standard" }: CardViewProps) {
  return (
    <div
      className={classNames(styles["root"], variantClassMap[variant], widthClassMap[width], className)}
    >
      {children}
    </div>
  );
}
