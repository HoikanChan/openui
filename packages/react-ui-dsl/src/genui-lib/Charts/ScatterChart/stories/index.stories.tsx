import type { Meta, StoryObj } from "@storybook/react";
import { ScatterChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/ScatterChart",
  component: ScatterChartView,
  args: {
    xLabel: "Latency (ms)",
    yLabel: "Packet Loss (%)",
    datasets: [
      { name: "Core Routers", points: [{ x: 5, y: 0.1 }, { x: 8, y: 0.2 }, { x: 12, y: 0.3 }] },
      { name: "Edge Switches", points: [{ x: 15, y: 0.5 }, { x: 22, y: 1.2 }, { x: 35, y: 2.1 }] },
    ],
  },
} satisfies Meta<typeof ScatterChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
