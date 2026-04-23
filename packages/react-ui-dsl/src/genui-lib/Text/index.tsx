"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TextSchema } from "./schema";
import styles from "./text.module.css";
import { TextView } from "./view";

const sizeClassMap: Record<NonNullable<z.infer<typeof TextSchema>["size"]>, string> = {
  small: styles["small"],
  default: styles["default"],
  large: styles["large"],
  "small-heavy": styles["smallHeavy"],
  "large-heavy": styles["largeHeavy"],
};

export const Text = defineComponent({
  name: "Text",
  props: TextSchema,
  description:
    'Text block with optional size. size: "small" | "default" | "large" | "small-heavy" | "large-heavy".',
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextSchema>>) => {
    const size = props.size ?? "default";

    return (
      <span className={sizeClassMap[size]}>
        <TextView content={props.text} />
      </span>
    );
  },
});
