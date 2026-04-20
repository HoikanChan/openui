"use client";

import type { CSSProperties } from "react";
import ReactMarkdown from "react-markdown";

export type TextViewProps = {
  content: string;
  style?: CSSProperties;
  type?: "default" | "markdown" | "html";
};

export function TextView({ content, style, type = "default" }: TextViewProps) {
  if (type === "html") {
    return <div dangerouslySetInnerHTML={{ __html: content }} style={style} />;
  }

  if (type === "markdown") {
    return (
      <div style={style}>
        <ReactMarkdown>{content}</ReactMarkdown>
      </div>
    );
  }

  return <span style={style}>{content}</span>;
}
