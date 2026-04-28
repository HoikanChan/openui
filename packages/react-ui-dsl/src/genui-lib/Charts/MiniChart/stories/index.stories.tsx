import type { Meta, StoryObj } from "@storybook/react";
import React from "react";
import { MiniChartView } from "../view";

const cardStyle: React.CSSProperties = {
  width: 220,
  padding: 16,
  borderRadius: 16,
  border: "1px solid #e5e7eb",
  background: "linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)",
  boxShadow: "0 10px 24px rgba(15, 23, 42, 0.06)",
};

const labelStyle: React.CSSProperties = {
  fontSize: 12,
  color: "#64748b",
  marginBottom: 8,
};

const valueStyle: React.CSSProperties = {
  fontSize: 24,
  fontWeight: 700,
  color: "#0f172a",
  marginBottom: 12,
};

const meta = {
  title: "DSL Components/Charts/MiniChart",
  component: MiniChartView,
  args: {
    type: "line",
    data: [12, 18, 15, 21, 19, 24, 22],
    size: 96,
    color: "#1677ff",
  },
} satisfies Meta<typeof MiniChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const LineCard: Story = {
  render: (args) => (
    <div style={cardStyle}>
      <div style={labelStyle}>7-Day Latency Trend</div>
      <div style={valueStyle}>22 ms</div>
      <MiniChartView {...args} />
    </div>
  ),
};

export const BarCard: Story = {
  args: {
    type: "bar",
    data: [
      { value: 3, label: "Mon" },
      { value: 5, label: "Tue" },
      { value: 4, label: "Wed" },
      { value: 6, label: "Thu" },
      { value: 5, label: "Fri" },
    ],
    color: "#16a34a",
  },
  render: (args) => (
    <div style={cardStyle}>
      <div style={labelStyle}>Deployments This Week</div>
      <div style={valueStyle}>23</div>
      <MiniChartView {...args} />
    </div>
  ),
};

export const AreaCard: Story = {
  args: {
    type: "area",
    data: [44, 48, 46, 52, 50, 56, 54],
    color: "#f97316",
  },
  render: (args) => (
    <div style={cardStyle}>
      <div style={labelStyle}>CPU Utilization</div>
      <div style={valueStyle}>54%</div>
      <MiniChartView {...args} />
    </div>
  ),
};
