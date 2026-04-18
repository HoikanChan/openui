"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { ImageSchema } from "./schema";

export const Image = defineComponent({
  name: "Image",
  props: ImageSchema,
  description: "Image — url, base64, or inline SVG",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ImageSchema>>) => {
    const { type, content } = props.properties;
    const style = props.style as React.CSSProperties | undefined;

    if (type === "svg") {
      return <div style={style} dangerouslySetInnerHTML={{ __html: content }} />;
    }
    const src = type === "base64" ? `data:image/*;base64,${content}` : content;
    return <img src={src} style={{ maxWidth: "100%", ...style }} alt="" />;
  },
});
