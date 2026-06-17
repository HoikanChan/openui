"use client";

import React from "react";
import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PreviewCardSchema } from "./schema";
import { canvasStore } from "../canvasStore";

export { PreviewCardSchema } from "./schema";
export type { PreviewCardProps } from "./schema";

function extractHtmlLoaderInfo(children: unknown[]): { url: string; iframeId: string; data?: Record<string, unknown> } | undefined {
  for (const child of children) {
    if (child?.typeName === "HTMLLoader") {
      return {
        url: child.props.url as string,
        iframeId: child.props.iframeId as string,
        data: child.props.data as Record<string, unknown> | undefined,
      };
    }
  }
  return undefined;
}

export const PreviewCard = defineComponent({
  name: "PreviewCard",
  props: PreviewCardSchema,
  description:
    'Preview card that shows a small card in LUI. Click adds full content to canvas PreviewTab. Args: children (required, full preview content or HTMLLoader), title (required, tab name), summary (optional, summary content shown in LUI small card), type (optional, "replace" or "append", default "append"), tabId (optional, target tab identifier), cardId (optional, card unique identifier for deduplication). If tabId exists: type="replace" replaces card with same cardId or adds new card; type="append" always adds a new card to the tab. If tabId absent or not found: creates a new tab. HTMLLoader children will render as iframe with bidirectional communication.',
  component: ({
    props,
    renderNode,
  }: ComponentRenderProps<z.infer<typeof PreviewCardSchema>>) => {
    const handleClick = () => {
      const loaderInfo = extractHtmlLoaderInfo(props.children);
      canvasStore.addPreviewCard(
        {
          title: props.title,
          children: props.children,
          url: loaderInfo?.url,
          iframeId: loaderInfo?.iframeId,
          data: loaderInfo?.data,
          cardId: props.cardId,
        },
        props.tabId,
        props.type,
      );
    };

    const summaryContent = props.summary ? renderNode(props.summary) : null;

    return (
      <div
        onClick={handleClick}
        style={{
          padding: "8px 16px",
          border: "1px solid #d9d9d9",
          borderRadius: 8,
          cursor: "pointer",
          backgroundColor: "#fff",
          display: "inline-block",
          maxWidth: 200,
        }}
      >
        <div style={{ fontWeight: 500, marginBottom: summaryContent ? 4 : 0 }}>
          {props.title}
        </div>
        {summaryContent}
      </div>
    );
  },
});
