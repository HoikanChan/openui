"use client";

import {
  defineComponent,
  type ComponentRenderProps,
  type SubComponentOf,
} from "@cloudsop/openui-react-lang";
import React from "react";
import { TabItemSchema, type TabItemProps } from "../TabItem";
import { TabsSchema, type TabsProps } from "./schema";
import { TabView } from "./view";

function isComponentNode<T>(value: unknown): value is SubComponentOf<T> {
  return typeof value === "object" && value !== null && "type" in value && "props" in value;
}

function getTabItemProps(value: unknown): TabItemProps {
  return isComponentNode<TabItemProps>(value) ? value.props : TabItemSchema.parse(value);
}

export const Tabs = defineComponent({
  name: "Tabs",
  props: TabsSchema,
  description: "Tabbed container with streaming-aware skeleton and auto tab-switching",
  component: ({ props, renderNode }: ComponentRenderProps<TabsProps>) => {
    const items = props.items.map(getTabItemProps);
    const [activeTab, setActiveTab] = React.useState("");
    const userHasInteracted = React.useRef(false);
    const prevContentSizes = React.useRef<Record<string, number>>({});

    React.useEffect(() => {
      const first = items[0];
      if (items.length && !activeTab && first) {
        setActiveTab(first.value);
      }
    }, [items.length, activeTab]);

    React.useEffect(() => {
      if (userHasInteracted.current) return;

      let candidate: string | null = null;
      const nextSizes: Record<string, number> = {};

      for (const item of items) {
        const size = JSON.stringify(item.content).length;
        const prevSize = prevContentSizes.current[item.value] ?? 0;
        nextSizes[item.value] = size;
        if (size > prevSize) {
          candidate = item.value;
        }
      }

      prevContentSizes.current = nextSizes;

      if (candidate && candidate !== activeTab) {
        setActiveTab(candidate);
      }
    });

    const handleTabChange = (value: string) => {
      userHasInteracted.current = true;
      setActiveTab(value);
    };

    if (!items.length) return null;

    return (
      <TabView
        activeTab={activeTab}
        onTabChange={handleTabChange}
        items={items.map((item) => ({
          value: item.value,
          label: item.label,
          children: renderNode(item.content),
          loading: item.content.length === 0,
        }))}
      />
    );
  },
});
