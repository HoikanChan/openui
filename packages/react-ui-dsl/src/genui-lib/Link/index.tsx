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
      disabled={props.disabled}
      download={props.download}
      href={props.href}
      target={props.target}
      text={props.text}
    />
  ),
});
