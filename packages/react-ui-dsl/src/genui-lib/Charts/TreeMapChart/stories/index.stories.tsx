import type { Meta, StoryObj } from "@storybook/react";
import { TreeMapChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/TreeMapChart",
  component: TreeMapChartView,
  args: {
    data: [
      { name: "eth0", value: 850, group: "Subnet A (192.168.1.0/24)" },
      { name: "eth1", value: 620, group: "Subnet A (192.168.1.0/24)" },
      { name: "eth2", value: 340, group: "Subnet B (10.0.0.0/24)" },
      { name: "eth3", value: 280, group: "Subnet B (10.0.0.0/24)" },
      { name: "Loopback", value: 5 },
    ],
  },
} satisfies Meta<typeof TreeMapChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
