import type { Meta, StoryObj } from "@storybook/react";
import { Button } from "antd";
import { CardView } from "../view";

const meta = {
  title: "DSL Components/Card",
  component: CardView,
  args: {
    header: {
      subtitle: "Deployment health",
      title: "Runtime rollout",
    },
    variant: "card",
    width: "standard",
  },
  argTypes: {
    variant: {
      control: "select",
      options: ["card", "clear", "sunk"],
    },
    width: {
      control: "select",
      options: ["standard", "full"],
    },
    header: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
  render: (args) => (
    <CardView
      {...args}
      header={{
        ...args.header,
        actions: <Button size="small">Inspect</Button>,
      }}
    >
      Stories now render directly from view props without booting the DSL runtime.
    </CardView>
  ),
} satisfies Meta<typeof CardView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
