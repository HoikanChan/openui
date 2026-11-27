import type { Meta, StoryObj } from "@storybook/react";
import { Tag } from "antd";
import { TimeLineView } from "../view";

const meta = {
  title: "DSL Components/TimeLine",
  component: TimeLineView,
  args: {
    items: [],
    title: "Release history",
  },
  argTypes: {
    style: {
      control: "object",
    },
  },
  render: (args) => (
    <TimeLineView
      {...args}
      items={[
        { content: <Tag color="green">All checks passed</Tag>, iconType: "success", title: "Build" },
        { content: <Tag color="blue">Controls wired</Tag>, iconType: "default", title: "Storybook" },
        { content: <Tag color="red">Manual QA pending</Tag>, iconType: "error", title: "Release" },
      ]}
    />
  ),
} satisfies Meta<typeof TimeLineView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
