"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { HTMLLoaderSchema } from "./schema";

export { HTMLLoaderSchema } from "./schema";
export type { HTMLLoaderProps } from "./schema";

export const HTMLLoader = defineComponent({
  name: "HTMLLoader",
  props: HTMLLoaderSchema,
  description:
    'HTMLLoader component for iframe embedding with bidirectional communication. Used as PreviewCard children. Args: url (required, iframe source), iframeId (required, unique identifier for postMessage communication), data (optional, object sent to iframe after it signals ready). Tab name comes from parent PreviewCard.title. Communication protocol: iframe sends {type: "openui-ready", iframeId} when loaded; HTMLLoader sends {type: "openui-data", iframeId, data} back; iframe can send {type: "openui-close", iframeId} to request tab removal.',
  component: ({
    props,
  }: ComponentRenderProps<z.infer<typeof HTMLLoaderSchema>>) => {
    return null;
  },
});