import type { Meta, StoryObj } from "@storybook/react";
import { RadarChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/RadarChart",
  component: RadarChartView,
  args: {
    labels: ["CPU %", "Memory %", "Disk %", "Bandwidth %", "Packet Loss %"],
    series: [
      { category: "Router-A", values: [65, 72, 45, 80, 2] },
      { category: "Router-B", values: [40, 55, 30, 60, 0.5] },
    ],
  },
} satisfies Meta<typeof RadarChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
