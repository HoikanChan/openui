import type { Meta, StoryObj } from "@storybook/react";
import { ButtonView } from "../view";

const meta = {
  title: "DSL Components/Button",
  component: ButtonView,
  args: {
    disabled: false,
    status: "primary",
    text: "Run action",
    type: "default",
  },
  argTypes: {
    status: {
      control: "select",
      options: ["default", "primary", "risk"],
    },
    type: {
      control: "select",
      options: ["default", "text"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof ButtonView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
