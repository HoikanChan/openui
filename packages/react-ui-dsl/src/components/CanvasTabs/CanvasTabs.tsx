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

  for (const [tabName, cards] of Object.entries(state.tabs)) {
    if (cards.length > 0) {
      tabs.push({
        key: `dashboard-${tabName}`,
        label: tabName,
        closable: true,
        children: <DashboardGrid cards={cards} library={library} dataModel={dataModel} />,
      });
    }
  }

  for (const tab of state.previewTabs) {
    if (tab.url && tab.iframeId) {
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: <HTMLLoaderEmbed url={tab.url} iframeId={tab.iframeId} data={tab.data} tabId={tab.tabId} />,
      });
    } else if (tab.url) {
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: <HTMLLoaderEmbed url={tab.url} iframeId={tab.tabId} tabId={tab.tabId} />,
      });
    } else {
      tabs.push({
        key: tab.tabId,
        label: tab.title,
        closable: true,
        children: (
          <div style={{ width: "100%", minHeight: "calc(100vh - 120px)", padding: 16 }}>
            {renderElementNode(tab.children, library, dataModel)}
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
    if (key.startsWith("dashboard-")) {
      const tabName = key.replace("dashboard-", "");
      canvasStore.removeDashboardTab(tabName);
    } else {
      canvasStore.removePreviewTab(key);
    }
  };

  const tabItems = useMemo(
    () => buildTabsFromState(state, library, dataModel),
    [state, library, dataModel]
  );

  const handleClear = () => {
    canvasStore.clear();
    onClear?.();
  };

  if (tabItems.length === 0) {
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