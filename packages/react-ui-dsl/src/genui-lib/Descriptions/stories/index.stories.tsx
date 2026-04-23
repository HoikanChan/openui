import type { Meta, StoryObj } from "@storybook/react";
import { TagView } from "../../Tag";
import { DescriptionsView } from "../view";

const meta = {
  title: "DSL Components/Descriptions",
  component: DescriptionsView,
  parameters: {
    docs: {
      description: {
        component:
          "Metric-card style descriptions layout for object-detail UIs. Top-level items can be ungrouped fields or titled groups.",
      },
    },
  },
  args: {
    columns: 3,
    title: "Employee Profile",
    items: [
      { kind: "field", label: "Name", renderedValue: "Alice", resolvedSpan: 1 },
      { kind: "field", label: "Email", renderedValue: "alice@example.com", resolvedSpan: 2 },
      {
        kind: "group",
        title: "Account",
        columns: 2,
        fields: [
          { kind: "field", label: "Status", renderedValue: <TagView text="Active" variant="success" />, resolvedSpan: 1 },
          { kind: "field", label: "Joined", renderedValue: "1/2/2026", resolvedSpan: 1 },
        ],
      },
    ],
  },
} satisfies Meta<typeof DescriptionsView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
