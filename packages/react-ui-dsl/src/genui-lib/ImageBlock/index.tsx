"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ImageBlockSchema } from "./schema";
import { ImageBlockView } from "./view";

export * from "./schema";
export * from "./view";

export const ImageBlock = defineComponent({
  name: "ImageBlock",
  props: ImageBlockSchema,
  description: "Image block with loading state",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ImageBlockSchema>>) => (
    <ImageBlockView alt={props.alt} src={props.src} />
  ),
});
