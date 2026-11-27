"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { InputSchema } from "./schema";
import { InputView } from "./view";

export const Input = defineComponent({
  name: "Input",
  props: InputSchema,
  description: "Single-line text input control",
  component: ({ props }: ComponentRenderProps<z.infer<typeof InputSchema>>) => (
    <InputView placeholder={props.placeholder} type={props.type} value={props.value} />
  ),
});
