"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Button as AntButton } from "antd";
import { z } from "zod";
import { ButtonSchema } from "./schema";

export function resolveButtonAppearance(props?: z.infer<typeof ButtonSchema>["properties"]) {
  const { status, type } = props ?? {};

  return {
    antType: type === "text" ? "text" : status === "primary" ? "primary" : "default",
    danger: status === "risk",
  } as const;
}

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ButtonSchema>>) => {
    const { text, disabled } = props.properties ?? {};
    const { antType, danger } = resolveButtonAppearance(props.properties);
    return (
      <AntButton
        type={antType}
        danger={danger}
        disabled={disabled}
        style={props.style as React.CSSProperties}
      >
        {text}
      </AntButton>
    );
  },
});
