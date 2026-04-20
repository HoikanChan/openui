import type { Meta, StoryObj } from "@storybook/react";
import { Card } from "antd";
import { VLayoutView } from "../view";

const meta = {
  title: "DSL Components/VLayout",
  component: VLayoutView,
  args: {
    gap: 16,
  },
  argTypes: {
    style: {
      control: "object",
    },
  },
  render: (args) => (
    <VLayoutView {...args}>
      <Card size="small" title="Pipeline">
        Source changes are batched into one release train.
      </Card>
      <Card size="small" title="Rollout">
        Storybook controls verify component props before publishing.
      </Card>
    </VLayoutView>
  ),
} satisfies Meta<typeof VLayoutView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
