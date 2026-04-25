"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { SeparatorSchema } from "./schema";
import styles from "./separator.module.css";

export * from "./schema";

export const Separator = defineComponent({
  name: "Separator",
  props: SeparatorSchema,
  description: "Visual divider between content sections",
  component: ({ props }: ComponentRenderProps<z.infer<typeof SeparatorSchema>>) => {
    const orientation = props.orientation ?? "horizontal";
    const className =
      orientation === "vertical"
        ? `openui-separator ${styles["root"]} ${styles["vertical"]}`
        : `openui-separator ${styles["root"]} ${styles["horizontal"]}`;

    return (
      <div
        aria-hidden={props.decorative || undefined}
        className={className}
        data-orientation={orientation}
        role={props.decorative ? undefined : "separator"}
      />
    );
  },
});
