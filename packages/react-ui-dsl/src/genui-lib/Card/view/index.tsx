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
  style?: CSSProperties;
  variant?: "card" | "clear" | "sunk";
  width?: "standard" | "full";
};

export function CardView({
  children,
  header,
  style,
  variant = "card",
  width = "standard",
}: CardViewProps) {
  const hasHeader = header && (header.title || header.subtitle || header.actions);

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
          {header.actions ? <div className={styles["headerActions"]}>{header.actions}</div> : null}
        </div>
      )}
      <div className={styles["body"]}>{children}</div>
    </div>
  );
}
