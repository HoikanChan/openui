import type { Meta, StoryObj } from "@storybook/react";
import { LineChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/LineChart",
  component: LineChartView,
  args: {
    labels: ["00:00", "04:00", "08:00", "12:00", "16:00", "20:00"],
    series: [
      { category: "Latency (ms)", values: [12, 14, 45, 38, 22, 18] },
      { category: "Packet Loss (%)", values: [0.1, 0.2, 1.5, 0.8, 0.3, 0.2] },
    ],
    xLabel: "Time",
  },
} satisfies Meta<typeof LineChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Smooth: Story = { args: { variant: "smooth" } };
export const Step: Story = { args: { variant: "step" } };
