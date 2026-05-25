"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { MarkdownView } from "../Components/MarkdownView";
import { MarkDownRendererSchema } from "./schema";
import styles from "./markDownRenderer.module.css";

export const MarkDownRenderer = defineComponent({
  name: "MarkDownRenderer",
  props: MarkDownRendererSchema,
  description:
    'Renders markdown text with optional container variant ("clear" | "card" | "sunk"). Use for long-form documents, README content, or markdown with code blocks. For short UI text and labels, use TextContent.',
  component: ({ props }: ComponentRenderProps<z.infer<typeof MarkDownRendererSchema>>) => {
    const variant = props.variant ?? "clear";
    return (
      <div className={`${styles["root"]} ${styles[variant]}`}>
        <MarkdownView content={props.textMarkdown} />
      </div>
    );
  },
});
