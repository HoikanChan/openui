import type { Meta, StoryObj } from "@storybook/react";
import { PieChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/PieChart",
  component: PieChartView,
  args: {
    labels: ["TCP", "UDP", "HTTP", "HTTPS", "Other"],
    values: [45, 20, 15, 15, 5],
  },
} satisfies Meta<typeof PieChartView>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Donut: Story = { args: { variant: "donut" } };
