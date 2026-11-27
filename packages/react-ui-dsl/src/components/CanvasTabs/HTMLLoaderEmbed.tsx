"use client";

import { useEffect, useRef, useCallback } from "react";
import { canvasStore } from "../../canvas/canvasStore";

export interface HTMLLoaderEmbedProps {
  url: string;
  iframeId: string;
  data?: Record<string, unknown>;
  tabId: string;
}

export function HTMLLoaderEmbed({ url, iframeId, data, tabId }: HTMLLoaderEmbedProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const readyRef = useRef(false);

  const handleMessage = useCallback(
    (event: MessageEvent) => {
      if (event.data?.iframeId !== iframeId) return;

      if (event.data?.type === "openui-ready") {
        readyRef.current = true;
        if (iframeRef.current?.contentWindow && data) {
          iframeRef.current.contentWindow.postMessage(
            { type: "openui-data", iframeId, data },
            "*"
          );
        }
      }

      if (event.data?.type === "openui-close") {
        canvasStore.removePreviewTab(tabId);
      }
    },
    [iframeId, data, tabId]
  );

  useEffect(() => {
    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [handleMessage]);

  return (
    <iframe
      ref={iframeRef}
      src={url}
      name={iframeId}
      style={{
        width: "100%",
        height: "calc(100vh - 120px)",
        minHeight: "500px",
        border: "none",
      }}
      title={iframeId}
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
    />
  );
}