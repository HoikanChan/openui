"use client";

import type { ReactNode } from "react";
import { classNames } from "../../Card/classNames";
import styles from "../cardHeader.module.css";

export type CardHeaderViewProps = {
  className?: string;
  subtitle?: ReactNode;
  title?: ReactNode;
};

export function CardHeaderView({ className, subtitle, title }: CardHeaderViewProps) {
  if (!title && !subtitle) {
    return null;
  }

  return (
    <div className={classNames(styles["root"], className)}>
      {title ? <div className={styles["title"]}>{title}</div> : null}
      {subtitle ? <div className={styles["subtitle"]}>{subtitle}</div> : null}
    </div>
  );
}
