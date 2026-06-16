"use client";

import React, { useCallback } from "react";
import { createParser } from "@openuidev/lang-core";
import type { Library } from "@openuidev/react-lang";
import { canvasStore } from "./canvasStore";
import { CanvasTabs } from "../components/CanvasTabs/CanvasTabs";

const DEMO_DSL_TABLE = `root = Stack([header, table])
header = TextContent("Device List", "large")
table = Table([nameCol, statusCol], data.devices)
nameCol = Col("Name", "name")
statusCol = Col("Status", "status")`;

const DEMO_DSL_CHART = `root = Stack([title, chart])
title = TextContent("Traffic Trend", "large")
chart = LineChart(["Mon", "Tue", "Wed"], [Series("Inbound", [120, 200, 150])], "smooth", "Time", "Mbps")`;

const DEMO_DSL_KPI = `root = Stack([kpiCard])
kpiCard = Card([cardTitle, miniChart])
cardTitle = TextContent("7-Day Latency", "large")
miniChart = MiniChart("line", [45, 52, 38, 60, 55, 48, 42])`;

export interface CanvasDemoProps {
  library: Library;
  dataModel?: Record<string, unknown>;
}

export function CanvasDemo({ library, dataModel }: CanvasDemoProps) {
  const parser = createParser(library.toJSONSchema());

  const parseDslToChildren = useCallback(
    (dsl: string): unknown[] | null => {
      const result = parser.parse(dsl);
      if (result.root) return [result.root];
      return null;
    },
    [parser]
  );

  const handleAddPreviewCard = useCallback(
    (dsl: string, title: string) => {
      const children = parseDslToChildren(dsl);
      if (children) canvasStore.addPreviewTab({ title, children });
    },
    [parseDslToChildren]
  );

  const handleAddDashboardCard = useCallback(
    (dsl: string, title: string, tab?: string, size?: { w?: number }) => {
      const children = parseDslToChildren(dsl);
      if (children) canvasStore.addDashboardCard({ title, children, size }, tab);
    },
    [parseDslToChildren]
  );

  const handleAddHtmlLoader = useCallback(
    (url: string, iframeId: string, title: string) => {
      canvasStore.addPreviewTab({ title, children: [], url, iframeId });
    },
    []
  );

  const handleClear = useCallback(() => {
    canvasStore.clear();
  }, []);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100vh" }}>
      <div style={{ padding: 16, borderBottom: "1px solid #d9d9d9" }}>
        <h3 style={{ margin: 0, marginBottom: 12 }}>Canvas Store Demo</h3>

        <div style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
          <span style={{ fontWeight: 500 }}>PreviewCard:</span>
          <button onClick={() => handleAddPreviewCard(DEMO_DSL_TABLE, "Device List")} style={{ padding: "4px 12px", cursor: "pointer" }}>
            Table
          </button>
          <button onClick={() => handleAddPreviewCard(DEMO_DSL_CHART, "Traffic Trend")} style={{ padding: "4px 12px", cursor: "pointer" }}>
            Chart
          </button>
          <button onClick={() => handleAddPreviewCard(DEMO_DSL_KPI, "KPI Card")} style={{ padding: "4px 12px", cursor: "pointer" }}>
            KPI
          </button>
        </div>

        <div style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
          <span style={{ fontWeight: 500 }}>DashboardCard:</span>
          <button
            onClick={() => handleAddDashboardCard(DEMO_DSL_TABLE, "Devices", "Dashboard", { w: 6 })}
            style={{ padding: "4px 12px", cursor: "pointer" }}
          >
            Table (w=6)
          </button>
          <button
            onClick={() => handleAddDashboardCard(DEMO_DSL_CHART, "Traffic", "Dashboard", { w: 6 })}
            style={{ padding: "4px 12px", cursor: "pointer" }}
          >
            Chart (w=6)
          </button>
          <button
            onClick={() => handleAddDashboardCard(DEMO_DSL_TABLE, "Devices", "Network", { w: 12 })}
            style={{ padding: "4px 12px", cursor: "pointer" }}
          >
            Table (w=12, tab=Network)
          </button>
        </div>

        <div style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
          <span style={{ fontWeight: 500 }}>HTMLLoader:</span>
          <button
            onClick={() => handleAddHtmlLoader("https://example.com", "html-demo-1", "External Page")}
            style={{ padding: "4px 12px", cursor: "pointer" }}
          >
            example.com
          </button>
        </div>

        <button onClick={handleClear} style={{ padding: "4px 12px", cursor: "pointer", color: "#ff4d4f" }}>
          Clear All
        </button>
      </div>

      <div style={{ flex: 1, overflow: "auto" }}>
        <CanvasTabs library={library} dataModel={dataModel} showClearButton={false} />
      </div>
    </div>
  );
}