"use client";

import { useEffect, useRef } from "react";
import { type ComponentRenderProps, defineComponent } from "@cloudsop/openui-react-lang";
import { z } from "zod";
import { CanvasCardSchema } from "./schema";
import { canvasStore } from "../canvasStore";

export { CanvasCardSchema } from "./schema";
export type { CanvasCardProps } from "./schema";

export const CanvasCard = defineComponent({
  name: "CanvasCard",
  props: CanvasCardSchema,
  description:
    'Canvas dashboard card that adds content to intelligent canvas. LUI does not render this component. Args: children (required, card content), title (optional, card title), tab (optional, default "Dashboard", tab name to add to), size (optional, default w=6, grid width 1-12). Tab is auto-created if not exists.',
  component: ({
    props,
  }: ComponentRenderProps<z.infer<typeof CanvasCardSchema>>) => {
    const cardIdRef = useRef<string | null>(null);

    useEffect(() => {
      const prevCardId = cardIdRef.current;
      if (prevCardId) {
        canvasStore.removeCanvasCard(prevCardId);
      }
      const cardId = canvasStore.addCanvasCard({
        title: props.title,
        children: props.children,
        size: props.size,
      }, props.cardId);
      cardIdRef.current = cardId;

      return () => {
        if (cardIdRef.current) {
          canvasStore.removeCanvasCard(cardIdRef.current);
          cardIdRef.current = null;
        }
      };
    }, [props.title, props.children, props.size,  props.cardId]);

    return null;
  },
});