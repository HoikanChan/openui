"use client";

import ReactMarkdown from "react-markdown";
import styles from "./markdownView.module.css";

export type MarkdownViewProps = {
  content: string;
};

export function MarkdownView({ content }: MarkdownViewProps) {
  return (
    <div className={styles["markdown"]}>
      <ReactMarkdown>{content}</ReactMarkdown>
    </div>
  );
}
