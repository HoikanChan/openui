import type { Meta, StoryObj } from "@storybook/react";
import type { ReactNode } from "react";
import { Stack } from "../";

type StackStoryProps = {
  align?: "center" | "stretch";
  children?: ReactNode;
  direction?: "row" | "column";
  justify?: "start" | "center" | "between";
  wrap?: boolean;
};

function StackStoryView({ children, ...props }: StackStoryProps) {
  return (
    <Stack.component
      props={{ ...props, children: [] }}
      renderNode={() => children}
    />
  );
}

const meta = {
  title: "DSL Components/Stack",
  component: StackStoryView,
  args: {
    direction: "column",
  },
  argTypes: {
    align: {
      control: "select",
      options: ["center", "stretch"],
    },
    direction: {
      control: "select",
      options: ["row", "column"],
    },
    justify: {
      control: "select",
      options: ["start", "center", "between"],
    },
    wrap: { control: "boolean" },
  },
  render: (args) => (
    <StackStoryView {...args}>
      <div style={{ padding: 8, background: "#f0f0f0" }}>Item 1</div>
      <div style={{ padding: 8, background: "#e0e0e0" }}>Item 2</div>
      <div style={{ padding: 8, background: "#d0d0d0" }}>Item 3</div>
    </StackStoryView>
  ),
} satisfies Meta<typeof StackStoryView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Vertical: Story = {};

export const Horizontal: Story = {
  args: { direction: "row" },
};
