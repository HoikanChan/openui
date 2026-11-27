import type { Meta, StoryObj } from "@storybook/react";
import { GaugeChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/GaugeChart",
  component: GaugeChartView,
  args: {
    readings: [
      { name: "CPU", value: 76 },
      { name: "Memory", value: 54 },
    ],
    min: 0,
    max: 100,
  },
} satisfies Meta<typeof GaugeChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const HighLatency: Story = {
  args: { readings: [{ name: "Latency", value: 120 }], min: 0, max: 200 },
};
