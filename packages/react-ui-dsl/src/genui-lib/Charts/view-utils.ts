import type * as echarts from "echarts";

type ChartDataset = { source: number[][] } | undefined;
type ChartProperties = (Omit<echarts.EChartsOption, "title"> & { title?: string }) | undefined;

export function buildChartOption(
  properties?: ChartProperties,
  data?: ChartDataset,
): echarts.EChartsOption {
  const { title, ...rest } = properties ?? {};

  return {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
}
