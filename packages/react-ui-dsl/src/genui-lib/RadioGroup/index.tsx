"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { RadioGroupSchema } from "./schema";
import { RadioGroupView } from "./view";

export * from "./schema";

export const RadioGroup = defineComponent({
  name: "RadioGroup",
  props: RadioGroupSchema,
  description: "Radio group for selecting one option from a list",
  component: ({ props }: ComponentRenderProps<z.infer<typeof RadioGroupSchema>>) => (
    <RadioGroupView
      defaultValue={props.defaultValue}
      items={props.items.map((item) => item.props)}
      value={props.value}
    />
  ),
});
