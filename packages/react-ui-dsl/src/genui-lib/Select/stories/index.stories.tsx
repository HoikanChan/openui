import type { Meta, StoryObj } from "@storybook/react";
import { SelectView } from "../view";

const meta = {
  title: "DSL Components/Select",
  component: SelectView,
  args: {
    allowClear: true,
    defaultValue: "north-america",
    options: [
      { label: "North America", value: "north-america" },
      { label: "Europe", value: "europe" },
      { label: "Asia Pacific", value: "apac" },
    ],
  },
  argTypes: {
    options: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof SelectView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
