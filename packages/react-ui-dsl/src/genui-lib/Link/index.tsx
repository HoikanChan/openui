"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { LinkSchema } from "./schema";
import { LinkView } from "./view";

export const Link = defineComponent({
  name: "Link",
  props: LinkSchema,
  description: "Anchor link",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LinkSchema>>) => (
    <LinkView
      disabled={props.properties.disabled}
      download={props.properties.download}
      href={props.properties.href}
      style={props.style as React.CSSProperties}
      target={props.properties.target}
      text={props.properties.text}
    />
  ),
});
