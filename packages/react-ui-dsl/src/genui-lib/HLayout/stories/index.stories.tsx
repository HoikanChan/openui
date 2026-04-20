import type { Meta, StoryObj } from "@storybook/react";
import { Tag } from "antd";
import { HLayoutView } from "../view";

const meta = {
  title: "DSL Components/HLayout",
  component: HLayoutView,
  args: {
    gap: 12,
    wrap: true,
  },
  argTypes: {
    style: {
      control: "object",
    },
  },
  render: (args) => (
    <HLayoutView {...args}>
      <Tag color="blue">Build</Tag>
      <Tag color="green">Review</Tag>
      <Tag color="purple">Release</Tag>
      <Tag color="gold">Observe</Tag>
    </HLayoutView>
  ),
} satisfies Meta<typeof HLayoutView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
