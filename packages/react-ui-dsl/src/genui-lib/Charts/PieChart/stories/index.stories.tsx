import type { Meta, StoryObj } from "@storybook/react";
import { PieChartView } from "../view";

const meta = {
  title: "DSL Components/Charts/PieChart",
  component: PieChartView,
  args: {
    data: {
      source: [
        [1, 38],
        [2, 27],
        [3, 35],
      ],
    },
    properties: {
      series: [{ encode: { itemName: 0, value: 1 }, radius: "60%", type: "pie" }],
      title: "Traffic Split",
    },
  },
  argTypes: {
    data: {
      control: "object",
    },
    properties: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof PieChartView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
