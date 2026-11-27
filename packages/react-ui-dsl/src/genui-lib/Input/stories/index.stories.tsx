import type { Meta, StoryObj } from "@storybook/react";
import { InputView } from "../view";

const meta = {
  title: "DSL Components/Input",
  component: InputView,
  args: {
    placeholder: "Enter text",
    type: "text",
  },
  argTypes: {
    type: {
      control: "select",
      options: ["text", "email", "password", "number", "url"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof InputView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Types: Story = {
  render: (args) => (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <InputView {...args} placeholder="Text" type="text" />
      <InputView {...args} placeholder="Email" type="email" />
      <InputView {...args} placeholder="Password" type="password" />
      <InputView {...args} placeholder="Number" type="number" />
      <InputView {...args} placeholder="URL" type="url" />
    </div>
  ),
};

export const WithValue: Story = {
  args: {
    placeholder: "Controlled value",
    value: "Initial text",
  },
};
