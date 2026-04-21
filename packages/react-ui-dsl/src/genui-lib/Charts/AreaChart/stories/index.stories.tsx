import type { Meta, StoryObj } from "@storybook/react";
import { AreaChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/AreaChart",
  component: AreaChartView,
  args: {
    labels: ["00:00", "02:00", "04:00", "06:00", "08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00"],
    series: [
      { category: "Download (Mbps)", values: [120, 90, 75, 100, 350, 480, 520, 490, 440, 380, 300, 200] },
      { category: "Upload (Mbps)", values: [40, 30, 25, 35, 120, 180, 200, 175, 160, 140, 100, 70] },
    ],
    xLabel: "Time",
    yLabel: "Mbps",
  },
} satisfies Meta<typeof AreaChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Smooth: Story = { args: { variant: "smooth" } };
