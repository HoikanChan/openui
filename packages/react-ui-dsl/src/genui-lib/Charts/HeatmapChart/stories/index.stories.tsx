import type { Meta, StoryObj } from "@storybook/react";
import { HeatmapChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/HeatmapChart",
  component: HeatmapChartView,
  args: {
    xLabels: ["0h", "2h", "4h", "6h", "8h", "10h", "12h", "14h", "16h", "18h", "20h", "22h"],
    yLabels: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
    values: [
      [2, 1, 0, 1, 5, 8, 10, 12, 9, 7, 4, 3],
      [1, 0, 1, 2, 6, 9, 11, 10, 8, 6, 3, 2],
      [3, 1, 0, 0, 4, 7, 9, 11, 10, 8, 5, 2],
      [2, 2, 1, 0, 5, 8, 10, 9, 8, 7, 4, 3],
      [4, 2, 1, 1, 6, 10, 12, 11, 9, 8, 5, 4],
      [1, 0, 0, 0, 2, 3, 4, 5, 4, 3, 2, 1],
      [0, 0, 0, 0, 1, 2, 3, 4, 3, 2, 1, 0],
    ],
  },
} satisfies Meta<typeof HeatmapChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
