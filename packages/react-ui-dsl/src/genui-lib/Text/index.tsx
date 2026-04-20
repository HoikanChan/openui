"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TextSchema } from "./schema";
import { TextView } from "./view";

export const Text = defineComponent({
  name: "Text",
  props: TextSchema,
  description: "Text content that supports plain, markdown, and HTML",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TextSchema>>) => (
    <TextView
      content={props.content}
      type={props.type}
    />
  ),
});
