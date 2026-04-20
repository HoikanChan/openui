import { expect, type MockedFunction } from "vitest";
import type * as echarts from "echarts";

type FixtureVerifyContext = {
  echartsInit: MockedFunction<typeof echarts.init>;
};

export interface Fixture {
  id: string;
  prompt: string;
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
      prompt: "Show a pie chart of sales distribution by region using data.pieData",
      dataModel: {
        pieData: { source: [[1200000], [860000], [1050000]] },
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
      prompt: "Show monthly revenue trend as a line chart using data.lineData",
      dataModel: {
        lineData: { source: [[420000], [530000], [610000]] },
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
      prompt: "Compare quarterly revenue for two product lines as a bar chart using data.barData",
      dataModel: {
        barData: { source: [[800000, 920000], [350000, 410000]] },
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
      prompt: "Show a KPI gauge for system health score using data.gaugeData",
      dataModel: {
        gaugeData: { source: [[87]] },
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
  VLayout: [
    {
      id: "vlayout-gap",
      prompt: "Show two revenue text lines stacked vertically with a gap",
      dataModel: {},
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
      dataModel: {},
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
      prompt: "Show a region selector dropdown with North America, Europe, APAC options",
      dataModel: {},
      assert: {
        contains: ["ant-select", "North America"],
      },
    },
  ],
  Image: [
    {
      id: "image-url",
      prompt: "Show a logo image from a URL",
      dataModel: {},
      assert: {
        contains: ["<img", "example.com/logo.png"],
      },
    },
  ],
  Link: [
    {
      id: "link-report",
      prompt: "Show a link to the Q1 report that opens in a new tab",
      dataModel: {},
      assert: {
        contains: ["View Q1 Report", "reports.example.com"],
      },
    },
  ],
  Card: [
    {
      id: "card-kpi",
      prompt: "Show a card with Q1 Performance as title, January to March 2026 as subtitle, and revenue summary as body",
      dataModel: {},
      assert: {
        contains: ["Q1 Performance", "January to March 2026", "Revenue increased"],
      },
    },
  ],
  List: [
    {
      id: "list-action-items",
      prompt: "Show an unordered list of action items with a header",
      dataModel: {},
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
      dataModel: {},
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
