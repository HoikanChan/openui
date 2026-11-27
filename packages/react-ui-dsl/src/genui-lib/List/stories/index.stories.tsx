import type { Meta, StoryObj } from "@storybook/react";
import { ListView } from "../view";

const meta = {
  title: "DSL Components/List",
  component: ListView,
  args: {
    header: "Release checklist",
    isOrder: true,
    items: ["Review component props", "Adjust stories", "Build Storybook"],
  },
  argTypes: {
    items: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof ListView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
