import type { Meta, StoryObj } from "@storybook/react";
import { SelectView } from "../view";

const meta = {
  title: "DSL Components/Select",
  component: SelectView,
  args: {
    items: [
      { label: "North America", value: "north-america" },
      { label: "Europe", value: "europe" },
      { label: "Asia Pacific", value: "apac" },
    ],
    placeholder: "Choose a region",
    value: "north-america",
  },
  argTypes: {
    items: {
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
