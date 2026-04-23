"use client";

import type { CSSProperties } from "react";
import ReactMarkdown from "react-markdown";
import styles from "./textView.module.css";

export type TextViewProps = {
  content: string;
  style?: CSSProperties;
  type?: "default" | "markdown" | "html";
};

export function TextView({ content, style, type = "default" }: TextViewProps) {
  if (type === "html") {
    return (
      <div
        className={styles["html"]}
        dangerouslySetInnerHTML={{ __html: content }}
        style={style}
      />
    );
  }

  if (type === "markdown") {
    return (
      <div
        className={styles["markdown"]}
        style={style}
      >
        <ReactMarkdown>{content}</ReactMarkdown>
      </div>
    );
  }

  return (
    <span
      className={styles["default"]}
      style={style}
    >
      {content}
    </span>
  );
}
