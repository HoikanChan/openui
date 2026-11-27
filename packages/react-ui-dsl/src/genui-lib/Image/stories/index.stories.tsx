import type { Meta, StoryObj } from "@storybook/react";
import { ImageView } from "../view";

const meta = {
  title: "DSL Components/Image",
  component: ImageView,
  args: {
    alt: "Team working together",
    src: "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=900&q=80",
  },
  argTypes: {
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof ImageView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
