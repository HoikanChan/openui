import type { Meta, StoryObj } from "@storybook/react";
import { TagView } from "../view";

const meta = {
  title: "DSL Components/Tag",
  component: TagView,
  parameters: {
    docs: {
      description: {
        component:
          "Ant Design-backed tag. The optional `icon` prop is preserved as a token on the rendered element and is not resolved into a graphic automatically.",
      },
    },
  },
  args: {
    text: "Review",
    size: "md",
    variant: "info",
  },
  argTypes: {
    icon: {
      control: "text",
    },
    size: {
      control: "select",
      options: ["sm", "md", "lg"],
    },
    style: {
      control: "object",
    },
    variant: {
      control: "select",
      options: ["neutral", "info", "success", "warning", "danger"],
    },
  },
} satisfies Meta<typeof TagView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Success: Story = {
  args: {
    text: "Complete",
    variant: "success",
  },
};

export const Large: Story = {
  args: {
    text: "Priority",
    size: "lg",
    variant: "warning",
  },
};
