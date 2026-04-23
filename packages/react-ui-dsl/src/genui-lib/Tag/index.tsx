"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TagSchema } from "./schema";
import { TagView } from "./view";

export * from "./schema";
export * from "./view";

export const Tag = defineComponent({
  name: "Tag",
  props: TagSchema,
  description: "Compact semantic tag or badge with optional icon token",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TagSchema>>) => (
    <TagView icon={props.icon} size={props.size} text={props.text} variant={props.variant} />
  ),
});
