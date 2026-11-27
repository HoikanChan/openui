import type { Meta, StoryObj } from "@storybook/react";
import { BarChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/BarChart",
  component: BarChartView,
  args: {
    labels: ["eth0", "eth1", "eth2", "eth3"],
    series: [
      { category: "Inbound (Mbps)", values: [320, 450, 210, 180] },
      { category: "Outbound (Mbps)", values: [280, 390, 175, 160] },
    ],
  },
} satisfies Meta<typeof BarChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Stacked: Story = { args: { variant: "stacked" } };
