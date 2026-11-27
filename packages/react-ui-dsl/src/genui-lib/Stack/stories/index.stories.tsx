import type { Meta, StoryObj } from "@storybook/react";
import type { ReactNode } from "react";
import { Stack } from "../";

type StackStoryProps = {
  align?: "start" | "center" | "end" | "stretch" | "baseline";
  children?: ReactNode;
  direction?: "row" | "column";
  gap?: "none" | "xs" | "s" | "m" | "l" | "xl" | "2xl";
  justify?: "start" | "center" | "end" | "between" | "around" | "evenly";
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
    gap: "m",
  },
  argTypes: {
    align: {
      control: "select",
      options: ["start", "center", "end", "stretch", "baseline"],
    },
    direction: {
      control: "select",
      options: ["row", "column"],
    },
    gap: {
      control: "select",
      options: ["none", "xs", "s", "m", "l", "xl", "2xl"],
    },
    justify: {
      control: "select",
      options: ["start", "center", "end", "between", "around", "evenly"],
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
