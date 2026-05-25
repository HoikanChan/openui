"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { TagBlockSchema } from "./schema";
import { TagView } from "../Tag/view";

export * from "./schema";

export const TagBlock = defineComponent({
  name: "TagBlock",
  props: TagBlockSchema,
  description: "tags is an array of strings",
  component: ({ props }: ComponentRenderProps<z.infer<typeof TagBlockSchema>>) => (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
      {props.tags.map((tag, i) => (
        <TagView key={i} text={tag} />
      ))}
    </div>
  ),
});
