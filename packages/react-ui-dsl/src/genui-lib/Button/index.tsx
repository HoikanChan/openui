"use client";

import { defineComponent } from "@openuidev/react-lang";
import { Button as AntButton } from "antd";
import { ButtonSchema } from "./schema";

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }) => {
    const { status, text, disabled, type } = props.properties ?? {};
    const antType =
      type === "text" ? "text" : status === "primary" ? "primary" : "default";
    return (
      <AntButton
        type={antType}
        danger={status === "risk"}
        disabled={disabled}
        style={props.style as React.CSSProperties}
      >
        {text}
      </AntButton>
    );
  },
});
