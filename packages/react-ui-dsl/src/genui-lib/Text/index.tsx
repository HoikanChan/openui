"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import ReactMarkdown from "react-markdown";
import { z } from "zod";
import { TextSchema } from "./schema";

export const Text = defineComponent({
  name: "Text",
  props: TextSchema,
  description: "Text content — supports plain, markdown, and HTML",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextSchema>>) => {
    const { type = "default", content } = props.properties;
    const style = props.style as React.CSSProperties | undefined;

    if (type === "html") {
      return <div style={style} dangerouslySetInnerHTML={{ __html: content }} />;
    }
    if (type === "markdown") {
      return (
        <div style={style}>
          <ReactMarkdown>{content}</ReactMarkdown>
        </div>
      );
    }
    return <span style={style}>{content}</span>;
  },
});
