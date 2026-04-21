import type { Meta, StoryObj } from "@storybook/react";
import { HorizontalBarChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/HorizontalBarChart",
  component: HorizontalBarChartView,
  args: {
    labels: ["GigabitEthernet0/0", "GigabitEthernet0/1", "FastEthernet1/0", "FastEthernet1/1", "Loopback0"],
    series: [{ category: "Traffic (Mbps)", values: [850, 620, 340, 280, 5] }],
  },
} satisfies Meta<typeof HorizontalBarChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Stacked: Story = {
  args: {
    series: [
      { category: "Inbound (Mbps)", values: [500, 380, 200, 160, 3] },
      { category: "Outbound (Mbps)", values: [350, 240, 140, 120, 2] },
    ],
    variant: "stacked",
  },
};
