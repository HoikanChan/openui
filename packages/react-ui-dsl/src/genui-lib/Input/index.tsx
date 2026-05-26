"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { InputSchema } from "./schema";
import { InputView } from "./view";

export const Input = defineComponent({
  name: "Input",
  props: InputSchema,
  description: "Single-line text input control",
  component: ({ props }: ComponentRenderProps<z.infer<typeof InputSchema>>) => (
    <InputView
      defaultValue={props.defaultValue}
      disabled={props.disabled}
      hasError={props.hasError}
      maxLength={props.maxLength}
      placeholder={props.placeholder}
      readOnly={props.readOnly}
      size={props.size}
      type={props.type}
    />
  ),
});
