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
          "Descriptions-style layout for object-detail UIs with bordered and plain visual variants.",
      },
    },
  },
  args: {
    border: true,
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

export const Grouped: Story = {
  args: {
    border: true,
    columns: 3,
    title: "Order Summary",
    items: [
      { kind: "field", label: "Product", renderedValue: "Cloud Database", resolvedSpan: 1 },
      { kind: "field", label: "Billing", renderedValue: "Prepaid", resolvedSpan: 1 },
      { kind: "field", label: "Amount", renderedValue: "$80.00", resolvedSpan: 1 },
      {
        kind: "group",
        title: "Configuration",
        columns: 2,
        fields: [
          { kind: "field", label: "Time", renderedValue: "18:00:00", resolvedSpan: 1 },
          {
            kind: "field",
            label: "Config Info",
            renderedValue: (
              <ul>
                <li>Data disk type: MongoDB</li>
                <li>Database version: 3.4</li>
                <li>Storage space: 10 GB</li>
                <li>Region: East China 1</li>
              </ul>
            ),
            resolvedSpan: 2,
          },
          { kind: "field", label: "Status", renderedValue: <TagView text="Running" variant="success" />, resolvedSpan: 1 },
          { kind: "field", label: "Discount", renderedValue: "$20.00", resolvedSpan: 1 },
        ],
      },
    ],
  },
};

export const Plain: Story = {
  args: {
    border: false,
    columns: 4,
    title: "User Info",
    items: [
      { kind: "field", label: "UserName", renderedValue: "Zhou Maomao", resolvedSpan: 1 },
      { kind: "field", label: "Telephone", renderedValue: "1810000000", resolvedSpan: 1 },
      { kind: "field", label: "Live", renderedValue: "Hangzhou, Zhejiang", resolvedSpan: 1 },
      { kind: "field", label: "Remark", renderedValue: "empty", resolvedSpan: 1 },
      {
        kind: "field",
        label: "Address",
        renderedValue: "No. 18, Wantang Road, Xihu District, Hangzhou, Zhejiang, China",
        resolvedSpan: 4,
      },
    ],
  },
};
