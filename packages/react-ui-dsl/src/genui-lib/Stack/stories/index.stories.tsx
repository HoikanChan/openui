import type { Meta, StoryObj } from "@storybook/react";
import { StackView } from "../view";

const meta = {
  title: "DSL Components/Stack",
  component: StackView,
  args: {
    vertical: true,
    gap: 12,
  },
  argTypes: {
    vertical: { control: "boolean" },
    gap: { control: "number" },
    wrap: { control: "boolean" },
  },
  render: (args) => (
    <StackView {...args}>
      <div style={{ padding: 8, background: "#f0f0f0" }}>Item 1</div>
      <div style={{ padding: 8, background: "#e0e0e0" }}>Item 2</div>
      <div style={{ padding: 8, background: "#d0d0d0" }}>Item 3</div>
    </StackView>
  ),
} satisfies Meta<typeof StackView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Vertical: Story = {};

export const Horizontal: Story = {
  args: { vertical: false },
};
