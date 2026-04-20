"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ButtonSchema } from "./schema";
import { ButtonView } from "./view";

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ButtonSchema>>) => {
    return (
      <ButtonView
        disabled={props.properties?.disabled}
        status={props.properties?.status}
        style={props.style as React.CSSProperties}
        text={props.properties?.text}
        type={props.properties?.type}
      />
    );
  },
});
