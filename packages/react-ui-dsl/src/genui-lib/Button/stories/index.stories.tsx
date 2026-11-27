import type { Meta, StoryObj } from "@storybook/react";
import { ButtonView } from "../view";

const meta = {
  title: "DSL Components/Button",
  component: ButtonView,
  args: {
    disabled: false,
    label: "Run action",
    size: "normal",
    type: "default",
    variant: "primary",
  },
  argTypes: {
    variant: {
      control: "select",
      options: ["primary", "secondary", "tertiary"],
    },
    type: {
      control: "select",
      options: ["default", "text"],
    },
    size: {
      control: "select",
      options: ["extra-small", "small", "normal", "large"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof ButtonView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
