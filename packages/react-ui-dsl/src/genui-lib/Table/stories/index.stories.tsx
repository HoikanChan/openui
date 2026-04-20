import type { Meta, StoryObj } from "@storybook/react";
import { TableView } from "../view";

const meta = {
  title: "DSL Components/Table",
  component: TableView,
  args: {
    columns: [
      { field: "region", title: "Region" },
      { field: "revenue", options: { sortable: true }, title: "Revenue" },
      { field: "updated", options: { format: "date", tooltip: true }, title: "Updated" },
    ],
    rows: [
      { region: "North America", revenue: "$1.2M", updated: "2026-04-01T00:00:00.000Z" },
      { region: "Europe", revenue: "$860K", updated: "2026-04-03T00:00:00.000Z" },
      { region: "Asia Pacific", revenue: "$1.05M", updated: "2026-04-07T00:00:00.000Z" },
    ],
  },
  argTypes: {
    columns: {
      control: "object",
    },
    rows: {
      control: "object",
    },
    style: {
      control: "object",
    },
  },
} satisfies Meta<typeof TableView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};
