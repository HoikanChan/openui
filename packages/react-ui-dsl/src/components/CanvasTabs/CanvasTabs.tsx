"use client";

import { useEffect, useState, useMemo } from "react";
import type { Library } from "@openuidev/react-lang";
import { renderElementNode } from "@openuidev/react-lang";
import { Tabs } from "antd";
import { canvasStore, type CanvasStoreState } from "../../canvas/canvasStore";
import { DashboardGrid } from "./DashboardGrid";
import { HTMLLoaderEmbed } from "./HTMLLoaderEmbed";

export interface CanvasTabsProps {
  library: Library;
  dataModel?: Record<string, unknown>;
  showClearButton?: boolean;
  onClear?: () => void;
}

function buildTabsFromState(state: CanvasStoreState, library: Library, dataModel?: Record<string, unknown>) {
  const tabs: Array<{ key: string; label: string; closable: boolean; children: React.ReactNode }> = [];

  if (state.canvasCards.length > 0) {
    tabs.push({
      key: "canvas",
      label: "Dashboard",
      closable: false,
      children: <DashboardGrid cards={state.canvasCards} library={library} dataModel={dataModel} />,
    });
  }

  for (const tab of state.previewTabs) {
    const allCards = tab.cards;
    const hasIframe = allCards.some(c => c.url && c.iframeId);
    const hasDslContent = allCards.some(c => !c.url);

    if (hasIframe && !hasDslContent) {
      const card = allCards.find(c => c.url && c.iframeId)!;
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: <HTMLLoaderEmbed url={card.url!} iframeId={card.iframeId!} data={card.data} tabId={tab.tabId} />,
      });
    } else if (hasIframe) {
      const card = allCards.find(c => c.url && c.iframeId)!;
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: <HTMLLoaderEmbed url={card.url!} iframeId={card.iframeId!} data={card.data} tabId={tab.tabId} />,
      });
    } else {
      const children = allCards.flatMap(c => c.children);
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: (
          <div style={{ width: "100%", minHeight: "calc(100vh - 120px)", padding: 16 }}>
            {renderElementNode(children, library, dataModel)}
          </div>
        ),
      });
    }
  }

  return tabs as any;
}

export function CanvasTabs({ library, dataModel, showClearButton = true, onClear }: CanvasTabsProps) {
  const [state, setState] = useState<CanvasStoreState>(canvasStore.getSnapshot());

  useEffect(() => {
    const unsubscribe = canvasStore.subscribe(() => {
      setState(canvasStore.getSnapshot());
    });
    return unsubscribe;
  }, []);

  const handleClose = (key: string) => {
    if (key === "canvas") return;
    canvasStore.removePreviewTab(key);
  };

  const tabItems = useMemo(
    () => buildTabsFromState(state, library, dataModel),
    [state, library, dataModel]
  );

  const handleClear = () => {
    canvasStore.clear();
    onClear?.();
  };

  const hasData = state.canvasCards.length > 0 || state.previewTabs.length > 0;

  if (!hasData) {
    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          height: "100%",
          color: "#999",
          fontSize: 14,
        }}
      >
        No Canvas items. Add CanvasCard, PreviewCard, or HTMLLoader to DSL.
      </div>
    );
  }

  if (!state.enableMultiTab) {
    const content = state.activeKey === "canvas"
      ? <DashboardGrid cards={state.canvasCards} library={library} dataModel={dataModel} />
      : (() => {
          const tab = state.previewTabs[0];
          if (!tab) return null;
          const allCards = tab.cards;
          const iframeCard = allCards.find(c => c.url && c.iframeId);
          if (iframeCard) {
            return <HTMLLoaderEmbed url={iframeCard.url!} iframeId={iframeCard.iframeId!} data={iframeCard.data} tabId={tab.tabId} />;
          }
          const children = allCards.flatMap(c => c.children);
          return (
            <div style={{ width: "100%", minHeight: "calc(100vh - 120px)", padding: 16 }}>
              {renderElementNode(children, library, dataModel)}
            </div>
          );
        })();

    return (
      <div style={{ position: "relative", width: "100%", height: "100%" }}>
        {showClearButton && (
          <button
            onClick={handleClear}
            style={{
              position: "absolute",
              top: 8,
              right: 8,
              padding: "4px 12px",
              fontSize: 12,
              border: "1px solid #d9d9d9",
              borderRadius: 4,
              background: "#fff",
              cursor: "pointer",
            }}
          >
            清空画布
          </button>
        )}
        {content}
      </div>
    );
  }

  return (
    <div style={{ position: "relative", width: "100%", height: "100%" }}>
      {showClearButton && (
        <button
          onClick={handleClear}
          style={{
            position: "absolute",
            top: 8,
            right: 8,
            padding: "4px 12px",
            fontSize: 12,
            border: "1px solid #d9d9d9",
            borderRadius: 4,
            background: "#fff",
            cursor: "pointer",
          }}
        >
          清空画布
        </button>
      )}
      <Tabs
        activeKey={state.activeKey}
        onChange={canvasStore.setActiveKey}
        items={tabItems}
        type="editable-card"
        onEdit={(e, action) => {
          if (action === "remove" && typeof e === "string") {
            handleClose(e);
          }
        }}
      />
    </div>
  );
}
