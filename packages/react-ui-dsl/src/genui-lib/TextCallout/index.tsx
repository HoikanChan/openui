"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { TextCalloutSchema } from "./schema";
import { TextCalloutView } from "./view";

export * from "./schema";
export * from "./view";

export const TextCallout = defineComponent({
  name: "TextCallout",
  props: TextCalloutSchema,
  description:
    "Standalone callout for important context or guidance. Use only inside Stack or Card",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextCalloutSchema>>) => (
    <TextCalloutView description={props.description} title={props.title} variant={props.variant} />
  ),
});
