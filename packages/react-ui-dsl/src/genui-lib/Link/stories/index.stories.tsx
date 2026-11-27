import type { Meta, StoryObj } from "@storybook/react";
import { LinkView } from "../view";

const meta = {
  title: "DSL Components/Link",
  component: LinkView,
  args: {
    disabled: false,
    href: "https://openui.com",
    target: "_blank",
    text: "Open OpenUI",
  },
  argTypes: {
    target: {
      control: "select",
      options: ["_self", "_blank"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof LinkView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
