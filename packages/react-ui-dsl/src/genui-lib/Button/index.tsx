"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ButtonSchema } from "./schema";
import {
  ButtonView,
  resolveButtonAppearance as resolveButtonViewAppearance,
} from "./view";

export function resolveButtonAppearance(props?: z.infer<typeof ButtonSchema>) {
  return resolveButtonViewAppearance({
    status: props?.status,
    type: props?.type,
  });
}

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ButtonSchema>>) => (
    <ButtonView
      disabled={props.disabled}
      status={props.status}
      text={props.text}
      type={props.type}
    />
  ),
});
