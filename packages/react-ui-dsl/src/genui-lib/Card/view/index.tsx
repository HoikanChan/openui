"use client";

import type { CSSProperties, ReactNode } from "react";
import styles from "../card.module.css";

function cx(...classes: (string | undefined | false | null)[]): string {
  return classes.filter(Boolean).join(" ");
}

const variantClass: Record<string, string> = {
  card: styles["variantCard"]!,
  clear: styles["variantClear"]!,
  sunk: styles["variantSunk"]!,
};

export type CardViewProps = {
  children?: ReactNode;
  header?: {
    actions?: ReactNode;
    subtitle?: string;
    title?: string;
  };
  headerActions?: ReactNode;
  style?: CSSProperties;
  variant?: "card" | "clear" | "sunk";
  width?: "standard" | "full";
};

export function CardView({
  children,
  header,
  headerActions,
  style,
  variant = "card",
  width = "standard",
}: CardViewProps) {
  const resolvedHeaderActions = headerActions ?? header?.actions;
  const hasHeader = header && (header.title || header.subtitle || resolvedHeaderActions);

  return (
    <div
      className={cx(
        styles["root"],
        variantClass[variant],
        width === "full" ? styles["widthFull"] : styles["widthStandard"],
      )}
      style={style}
    >
      {hasHeader && (
        <div className={styles["header"]}>
          <div className={styles["headerLeft"]}>
            {header.title && <div className={styles["headerTitle"]}>{header.title}</div>}
            {header.subtitle && <div className={styles["headerSubtitle"]}>{header.subtitle}</div>}
          </div>
          {resolvedHeaderActions ? (
            <div className={styles["headerActions"]}>{resolvedHeaderActions}</div>
          ) : null}
        </div>
      )}
      <div className={styles["body"]}>{children}</div>
    </div>
  );
}
