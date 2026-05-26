import type { Meta, StoryObj } from "@storybook/react";
import { InputView } from "../view";

const meta = {
  title: "DSL Components/Input",
  component: InputView,
  args: {
    placeholder: "Enter text",
  },
  argTypes: {
    size: {
      control: "select",
      options: ["small", "medium", "large"],
    },
    type: {
      control: "select",
      options: ["text", "password", "email", "number", "tel", "url", "search"],
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof InputView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Sizes: Story = {
  render: (args) => (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <InputView {...args} size="small" placeholder="Small" />
      <InputView {...args} size="medium" placeholder="Medium" />
      <InputView {...args} size="large" placeholder="Large" />
    </div>
  ),
};

export const ErrorState: Story = {
  args: {
    hasError: true,
    placeholder: "Invalid value",
  },
};

export const Disabled: Story = {
  args: {
    disabled: true,
    placeholder: "Disabled input",
  },
};

export const ReadOnly: Story = {
  args: {
    readOnly: true,
    defaultValue: "Read-only value",
  },
};

export const Password: Story = {
  args: {
    type: "password",
    defaultValue: "hunter2",
  },
};

export const Number: Story = {
  args: {
    type: "number",
    maxLength: 6,
    placeholder: "0",
  },
};

export const WithDefaultValue: Story = {
  args: {
    defaultValue: "Initial text",
  },
};
