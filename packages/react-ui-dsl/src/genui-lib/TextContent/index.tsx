"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { MarkdownView } from "../Components/MarkdownView";
import { TextContentSchema } from "./schema";
import styles from "./textContent.module.css";

const sizeClassMap = {
  small: styles["small"],
  default: styles["default"],
  large: styles["large"],
  "small-heavy": styles["smallHeavy"],
  "large-heavy": styles["largeHeavy"],
} as const;

export const TextContent = defineComponent({
  name: "TextContent",
  props: TextContentSchema,
  description:
    'Text block. Supports markdown. Optional size: "small" | "default" | "large" | "small-heavy" | "large-heavy".',
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextContentSchema>>) => {
    const size = props.size ?? "default";
    return (
      <div className={sizeClassMap[size]}>
        <MarkdownView content={props.text} />
      </div>
    );
  },
});
