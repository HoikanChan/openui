"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { Typography } from "antd";
import { z } from "zod";
import { LinkSchema } from "./schema";

export const Link = defineComponent({
  name: "Link",
  props: LinkSchema,
  description: "Anchor link",
  component: ({ props }: ComponentRenderProps<z.infer<typeof LinkSchema>>) => {
    const { href, text, target, disabled, download } = props.properties;
    return (
      <Typography.Link
        href={disabled ? undefined : href}
        target={target}
        download={download}
        disabled={disabled}
        style={props.style as React.CSSProperties}
      >
        {text ?? href}
      </Typography.Link>
    );
  },
});
