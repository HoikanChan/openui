import type { Meta, StoryObj } from "@storybook/react";
import { CardHeaderView } from "../../CardHeader/view";
import { CardView } from "../view";

const meta = {
  title: "DSL Components/Card",
  component: CardView,
  args: {
    direction: "column",
  },
  argTypes: {
    direction: {
      control: "select",
      options: ["row", "column"],
    },
    align: {
      control: "select",
      options: ["start", "center", "end", "stretch", "baseline"],
    },
    justify: {
      control: "select",
      options: ["start", "center", "end", "between", "around", "evenly"],
    },
    wrap: {
      control: "boolean",
    },
    style: {
      control: "object",
    },
  },
  render: (args) => (
    <CardView {...args}>
      <CardHeaderView subtitle="Deployment health" title="Runtime rollout" />
      Stories now compose header content through a dedicated CardHeader child.
    </CardView>
  ),
} satisfies Meta<typeof CardView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
