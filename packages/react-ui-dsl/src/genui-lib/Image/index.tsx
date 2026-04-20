"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ImageSchema } from "./schema";
import { ImageView } from "./view";

export const Image = defineComponent({
  name: "Image",
  props: ImageSchema,
  description: "Image that supports url, base64, or inline SVG",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ImageSchema>>) => (
    <ImageView
      content={props.properties.content}
      style={props.style as React.CSSProperties}
      type={props.properties.type}
    />
  ),
});
