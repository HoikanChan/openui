import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { TabView } from "../view";

const meta = {
  title: "DSL Components/Tabs",
  component: TabView,
  args: {
    activeTab: "overview",
    onTabChange: () => {},
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: "Settings content", loading: false },
    ],
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
    onTabChange: () => {},
    items: [
      { value: "overview", label: "Overview", children: "Overview content", loading: false },
      { value: "settings", label: "Settings", children: null, loading: true },
    ],
  },
};
