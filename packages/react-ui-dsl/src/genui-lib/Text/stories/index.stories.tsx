import type { Meta, StoryObj } from "@storybook/react";
import { TextView } from "../view";

const meta = {
  title: "DSL Components/Text",
  component: TextView,
  args: {
    content: "### Release Notes\n\n- Controls can now drive props\n- Components render directly",
    type: "markdown",
  },
  argTypes: {
    type: {
      control: "select",
      options: ["default", "markdown", "html"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof TextView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
