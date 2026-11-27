"use client";
import { BaseChart } from "../../../../components/chart/BaseChart";
import type * as echarts from "echarts";

type TreeMapItem = { name: string; value: number; group?: string };

type TreeMapChartViewProps = {
  data: TreeMapItem[];
};

export function TreeMapChartView({ data }: TreeMapChartViewProps) {
  const grouped = new Map<string, { name: string; value: number }[]>();
  const ungrouped: { name: string; value: number }[] = [];

  for (const item of (data ?? [])) {
    if (item.group) {
      if (!grouped.has(item.group)) grouped.set(item.group, []);
      grouped.get(item.group)!.push({ name: item.name, value: item.value });
    } else {
      ungrouped.push({ name: item.name, value: item.value });
    }
  }

  const treeData = [
    ...ungrouped,
    ...Array.from(grouped.entries()).map(([name, children]) => ({
      name,
      value: children.reduce((sum, c) => sum + c.value, 0),
      children,
    })),
  ];

  const option: echarts.EChartsOption = {
    series: [{
      type: "treemap",
      data: treeData,
      label: { show: true, formatter: "{b}: {c}" },
    }],
    tooltip: { trigger: "item", formatter: "{b}: {c}" },
  };
  return <BaseChart option={option} />;
}
