"use client";

import { type ComponentRenderProps, defineComponent } from "@openuidev/react-lang";
import React from "react";
import { z } from "zod";
import { TabsSchema } from "./schema";
import { TabView } from "./view";

export const Tabs = defineComponent({
  name: "Tabs",
  props: TabsSchema,
  description: "Tabbed container with streaming-aware skeleton and auto tab-switching",
  component: ({ props, renderNode }: ComponentRenderProps<z.infer<typeof TabsSchema>>) => {
    const items = props.items ?? [];
    type TabItem = z.infer<typeof TabsSchema>["items"][number];
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
        style={props.style as React.CSSProperties}
        items={items.map((item: TabItem) => ({
          value: item.value,
          label: item.label,
          children: renderNode(item.content),
          loading: item.content.length === 0,
        }))}
      />
    );
  },
});
