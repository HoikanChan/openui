import type { Meta, StoryObj } from "@storybook/react";
import { GaugeChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/GaugeChart",
  component: GaugeChartView,
  args: {
    options: {
      series: [{ data: [{ value: 76 }], progress: { show: true }, type: "gauge" }],
      title: "Availability",
    },
  },
  argTypes: {
    data: {
      control: "object",
    },
    options: {
      control: "object",
    },
  },
} satisfies Meta<typeof GaugeChartView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
