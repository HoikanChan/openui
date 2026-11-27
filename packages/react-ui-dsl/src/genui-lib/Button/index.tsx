"use client";

import {
  type ComponentRenderProps,
  defineComponent,
  useFormName,
  useTriggerAction,
} from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ButtonSchema } from "./schema";
import { ButtonView, resolveButtonAppearance as resolveButtonViewAppearance } from "./view";

export function resolveButtonAppearance(props?: z.infer<typeof ButtonSchema>) {
  return resolveButtonViewAppearance({
    size: props?.size,
    type: props?.type,
    variant: props?.variant,
  });
}

export const Button = defineComponent({
  name: "Button",
  props: ButtonSchema,
  description: "Clickable button",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ButtonSchema>>) => {
    const triggerAction = useTriggerAction();
    const formName = useFormName();

    return (
      <ButtonView
        disabled={props.disabled}
        label={props.label}
        onClick={() => {
          void triggerAction(props.label, formName, props.action);
        }}
        size={props.size}
        type={props.type}
        variant={props.variant}
      />
    );
  },
});
