import { expect, type MockedFunction } from "vitest";
import type * as echarts from "echarts";

type FixtureVerifyContext = {
  echartsInit: MockedFunction<typeof echarts.init>;
};

export interface Fixture {
  id: string;
  prompt: string;
  expectedDescription?: string;
  dataModel: Record<string, unknown>;
  assert: {
    contains: string[];
    notContains?: string[];
    verify?: (container: HTMLElement, context: FixtureVerifyContext) => void;
  };
}

export const fixtures: Record<string, Fixture[]> = {
  Table: [
    {
      id: "table-basic",
      prompt: "Show a regional sales breakdown table",
      dataModel: {
        report: {
          breakdown: [
            { region: "North America", revenue: 1200000, updatedAt: "2026-04-01T00:00:00.000Z" },
            { region: "Europe", revenue: 860000, updatedAt: "2026-04-03T00:00:00.000Z" },
          ],
        },
      },
      assert: {
        contains: ["North America", "Europe", "Region"],
        notContains: ["T00:00:00.000Z"],
      },
    },
    {
      id: "table-sortable-date",
      prompt: "Show an employee table with sortable salary and formatted join date",
      dataModel: {
        employees: [
          { name: "Alice", salary: 95000, joinedAt: "2023-06-15T00:00:00.000Z" },
          { name: "Bob", salary: 82000, joinedAt: "2022-01-10T00:00:00.000Z" },
        ],
      },
      assert: {
        contains: ["Alice", "Bob", "Name", "Salary"],
        notContains: ["T00:00:00.000Z"],
        verify: (container) => {
          expect(
            container.innerHTML,
            "table-sortable-date: expected sortable column indicator (ant-table-column-sorter)",
          ).toContain("ant-table-column-sorter");
        },
      },
    },
  ],
  PieChart: [
    {
      id: "pie-sales-by-region",
      prompt: "Show a pie chart of sales distribution by region using data.labels and data.values",
      dataModel: {
        labels: ["North America", "Europe", "APAC"],
        values: [1200000, 860000, 1050000],
      },
      assert: {
        contains: [],
        verify: (container, { echartsInit }) => {
          expect(echartsInit, "pie-sales-by-region: echarts.init was not called").toHaveBeenCalled();
          expect(
            container.querySelector('div[style*="300px"]'),
            'pie-sales-by-region: no container with height "300px" found',
          ).not.toBeNull();
        },
      },
    },
  ],
  LineChart: [
    {
      id: "line-monthly-revenue",
      prompt: "Show monthly revenue trend as a line chart using data.labels and data.series",
      dataModel: {
        labels: ["Jan", "Feb", "Mar"],
        series: [{ category: "Revenue", values: [420000, 530000, 610000] }],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "line-monthly-revenue: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  BarChart: [
    {
      id: "bar-product-comparison",
      prompt: "Compare quarterly revenue for two product lines as a bar chart using data.labels and data.series",
      dataModel: {
        labels: ["Q1", "Q2"],
        series: [
          { category: "Product A", values: [800000, 920000] },
          { category: "Product B", values: [350000, 410000] },
        ],
      },
      assert: {
        contains: [],
        verify: (container, { echartsInit }) => {
          expect(echartsInit, "bar-product-comparison: echarts.init was not called").toHaveBeenCalled();
          expect(
            container.querySelector('div[style*="300px"]'),
            'bar-product-comparison: no container with height "300px" found',
          ).not.toBeNull();
        },
      },
    },
  ],
  GaugeChart: [
    {
      id: "gauge-kpi",
      prompt: "Show a KPI gauge for system health score using data.readings",
      dataModel: {
        readings: [{ name: "Health", value: 87 }],
      },
      assert: {
        contains: [],
        verify: (container, { echartsInit }) => {
          expect(echartsInit, "gauge-kpi: echarts.init was not called").toHaveBeenCalled();
          expect(
            container.querySelector('div[style*="300px"]'),
            'gauge-kpi: no container with height "300px" found',
          ).not.toBeNull();
        },
      },
    },
  ],
  HorizontalBarChart: [
    {
      id: "hbar-interface-traffic",
      prompt: "Show top interfaces by traffic as a horizontal bar chart using data.labels and data.series",
      dataModel: {
        labels: ["GigabitEthernet0/0", "GigabitEthernet0/1", "FastEthernet1/0"],
        series: [{ category: "Traffic (Mbps)", values: [850, 620, 340] }],
      },
      assert: {
        contains: [],
        verify: (container, { echartsInit }) => {
          expect(echartsInit, "hbar-interface-traffic: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  AreaChart: [
    {
      id: "area-bandwidth-utilization",
      prompt: "Show bandwidth utilization over 24 hours as an area chart using data.labels and data.series",
      dataModel: {
        labels: ["00:00", "06:00", "12:00", "18:00", "24:00"],
        series: [{ category: "Download (Mbps)", values: [120, 200, 520, 380, 200] }],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "area-bandwidth-utilization: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  RadarChart: [
    {
      id: "radar-device-health",
      prompt: "Compare device health metrics across routers as a radar chart using data.labels and data.series",
      dataModel: {
        labels: ["CPU %", "Memory %", "Disk %", "Bandwidth %", "Packet Loss %"],
        series: [
          { category: "Router-A", values: [65, 72, 45, 80, 2] },
          { category: "Router-B", values: [40, 55, 30, 60, 1] },
        ],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "radar-device-health: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  HeatmapChart: [
    {
      id: "heatmap-alert-frequency",
      prompt: "Show alert frequency by hour and day of week as a heatmap using data.xLabels, data.yLabels, and data.values",
      dataModel: {
        xLabels: ["0h", "6h", "12h", "18h"],
        yLabels: ["Mon", "Tue", "Wed"],
        values: [
          [2, 8, 12, 7],
          [1, 9, 11, 6],
          [3, 7, 9, 8],
        ],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "heatmap-alert-frequency: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  TreeMapChart: [
    {
      id: "treemap-bandwidth-breakdown",
      prompt: "Show bandwidth breakdown by subnet and interface as a treemap using data.data",
      dataModel: {
        data: [
          { name: "eth0", value: 850, group: "Subnet A" },
          { name: "eth1", value: 620, group: "Subnet A" },
          { name: "eth2", value: 340, group: "Subnet B" },
        ],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "treemap-bandwidth-breakdown: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  ScatterChart: [
    {
      id: "scatter-latency-vs-loss",
      prompt: "Show latency vs packet loss for core routers as a scatter chart using data.scatterSeries",
      dataModel: {
        scatterSeries: {
          name: "Core Routers",
          points: [{ x: 5, y: 0.1 }, { x: 8, y: 0.2 }, { x: 12, y: 0.3 }],
        },
        xLabel: "Latency (ms)",
        yLabel: "Packet Loss (%)",
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "scatter-latency-vs-loss: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  Series: [
    {
      id: "series-interface-traffic",
      prompt: "Show interface traffic as a bar chart with two Series for inbound and outbound using data.labels and data.series",
      dataModel: {
        labels: ["eth0", "eth1"],
        series: [
          { category: "Inbound", values: [320, 450] },
          { category: "Outbound", values: [280, 390] },
        ],
      },
      assert: {
        contains: [],
        verify: (_container, { echartsInit }) => {
          expect(echartsInit, "series-interface-traffic: echarts.init was not called").toHaveBeenCalled();
        },
      },
    },
  ],
  VLayout: [
    {
      id: "vlayout-gap",
      prompt: "Show two revenue text lines stacked vertically with a gap",
      dataModel: {
        report: {
          revenueLines: ["Q1 Revenue: $1.2M", "Q2 Revenue: $1.4M"],
        },
      },
      assert: {
        contains: ["Q1 Revenue: $1.2M", "Q2 Revenue: $1.4M"],
      },
    },
  ],
  HLayout: [
    {
      id: "hlayout-panels",
      prompt: "Show two panels side by side horizontally",
      dataModel: {},
      assert: {
        contains: ["Left Panel", "Right Panel"],
      },
    },
  ],
  Text: [
    {
      id: "text-markdown",
      prompt: "Show a markdown summary of Q1 results with a heading and bold growth figure",
      dataModel: {
        summary: {
          heading: "Q1 Results",
          growth: "15%",
        },
      },
      assert: {
        contains: ["Q1 Results", "15%"],
      },
    },
  ],
  Button: [
    {
      id: "button-primary",
      prompt: "Show a primary submit button labeled Submit Report",
      dataModel: {},
      assert: {
        contains: ["Submit Report", "ant-btn"],
      },
    },
  ],
  Select: [
    {
      id: "select-region",
      prompt: "Show a region selector dropdown using data.options and default it to North America",
      dataModel: {
        options: [
          { label: "North America", value: "na" },
          { label: "Europe", value: "eu" },
          { label: "APAC", value: "apac" },
        ],
        defaultValue: "na",
      },
      assert: {
        contains: ["ant-select", "North America"],
      },
    },
  ],
  Image: [
    {
      id: "image-url",
      prompt: "Show a logo image from a URL",
      dataModel: {
        branding: {
          logoUrl: "https://example.com/logo.png",
        },
      },
      assert: {
        contains: ["<img", "example.com/logo.png"],
      },
    },
  ],
  Link: [
    {
      id: "link-report",
      prompt: "Show a link to the Q1 report that opens in a new tab",
      dataModel: {
        report: {
          label: "View Q1 Report",
          url: "https://reports.example.com/q1",
        },
      },
      assert: {
        contains: ["View Q1 Report", "reports.example.com"],
      },
    },
  ],
  Card: [
    {
      id: "card-kpi",
      prompt: "Show a card with Q1 Performance as title",
      dataModel: {},
      assert: {
        contains: ["Q1 Performance",],
      },
    },
  ],
  List: [
    {
      id: "list-action-items",
      prompt:
        "Show an unordered list with the header Action Items and exactly these list items: Review Q1 financials and Update product roadmap",
      dataModel: {
        list: {
          title: "Action Items",
          items: ["Review Q1 financials", "Update product roadmap"],
        },
      },
      assert: {
        contains: ["Action Items", "Review Q1 financials", "Update product roadmap"],
        verify: (container) => {
          expect(
            container.querySelector("ul"),
            "list-action-items: expected <ul> element",
          ).not.toBeNull();
        },
      },
    },
  ],
  Form: [
    {
      id: "form-contact",
      prompt: "Show a vertical contact form with Full Name and Email Address fields",
      dataModel: {},
      assert: {
        contains: ["Full Name", "Email Address"],
      },
    },
  ],
  TimeLine: [
    {
      id: "timeline-deployments",
      prompt: "Show a deployment history timeline with success, default, and error events",
      dataModel: {
        timeline: {
          title: "Deployment History",
          items: [
            {
              status: "success",
              title: "v2.1.0 deployed to production",
              description: "Production deployment completed successfully.",
            },
            {
              status: "default",
              title: "v2.0.1 staged for rollout",
              description: "Rollout is pending approval.",
            },
            {
              status: "error",
              title: "v2.0.0 deployment failed",
              description: "Deployment failed during verification.",
            },
          ],
        },
      },
      assert: {
        contains: ["Deployment History", "v2.1.0 deployed to production", "v2.0.0 deployment failed"],
        verify: (container) => {
          expect(
            container.innerHTML,
            'timeline-deployments: expected class "ant-timeline"',
          ).toContain("ant-timeline");
        },
      },
    },
  ],
  Tabs: [
    {
      id: "tabs-overview-settings",
      prompt: "Show a tabbed layout with Overview and Settings tabs",
      dataModel: {},
      assert: {
        contains: ["Overview", "Settings"],
        verify: (container) => {
          expect(
            container.innerHTML,
            'tabs-overview-settings: expected class "ant-tabs"',
          ).toContain("ant-tabs");
        },
      },
    },
  ],
};
