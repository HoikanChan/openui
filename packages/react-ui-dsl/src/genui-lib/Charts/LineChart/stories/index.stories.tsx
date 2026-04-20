import type { Meta, StoryObj } from "@storybook/react";
import { LineChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/LineChart",
  component: LineChartView,
  args: {
    data: {
      source: [
        [1, 12],
        [2, 18],
        [3, 24],
      ],
    },
    properties: {
      series: [{ encode: { x: 0, y: 1 }, type: "line" }],
      title: "Request Throughput",
      xAxis: { type: "category" },
      yAxis: { type: "value" },
    },
  },
  argTypes: {
    data: {
      control: "object",
    },
    properties: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof LineChartView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
