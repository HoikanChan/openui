"use client";

import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { ImageSchema } from "./schema";
import { ImageView } from "./view";

export const Image = defineComponent({
  name: "Image",
  props: ImageSchema,
  description: "Image that supports url.",
  component: ({ props }: ComponentRenderProps<z.infer<typeof ImageSchema>>) => (
    <ImageView alt={props.alt} src={props.src} />
  ),
});
