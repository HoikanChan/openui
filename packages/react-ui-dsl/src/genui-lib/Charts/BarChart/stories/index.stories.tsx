import type { Meta, StoryObj } from "@storybook/react";
import { BarChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/BarChart",
  component: BarChartView,
  args: {
    options: {
      legend: {},
      series: [{ data: [120, 200, 150], type: "bar" }],
      title: "Quarterly Revenue",
      xAxis: { data: ["Q1", "Q2", "Q3"], type: "category" },
      yAxis: { type: "value" },
    },
  },
  argTypes: {
    options: {
      control: "object",
    },
  },
} satisfies Meta<typeof BarChartView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
