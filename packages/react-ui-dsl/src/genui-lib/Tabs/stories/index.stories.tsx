import { useState } from "react";
import type { Meta, StoryObj } from "@storybook/react";
import { TabView } from "../view";

const meta = {
  title: "DSL Components/Tabs",
  component: TabView,
  args: {
    activeTab: "overview",
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: "Settings content", loading: false },
    ],
  },
  argTypes: {
    style: { control: "object" },
  },
  render: (args) => {
    const [activeTab, setActiveTab] = useState(args.activeTab);
    return <TabView {...args} activeTab={activeTab} onTabChange={setActiveTab} />;
  },
} satisfies Meta<typeof TabView>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
  args: {
    activeTab: "overview",
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: null, loading: true },
    ],
  },
};
