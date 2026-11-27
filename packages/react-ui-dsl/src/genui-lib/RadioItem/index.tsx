"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { RadioItemSchema } from "./schema";
import { RadioItemView } from "./view";

export * from "./schema";

export const RadioItem = defineComponent({
  name: "RadioItem",
  props: RadioItemSchema,
  description: "Standalone radio toggle with optional label and description",
  component: ({ props }: ComponentRenderProps<z.infer<typeof RadioItemSchema>>) => (
    <RadioItemView description={props.description} label={props.label} value={props.value} />
  ),
});
