import type * as echarts from "echarts";

type ChartDataset = { source: number[][] } | undefined;
type ChartOptions = (Omit<echarts.EChartsOption, "title"> & { title?: string }) | undefined;

export function buildChartOption(
  options?: ChartOptions,
  data?: ChartDataset,
): echarts.EChartsOption {
  const { title, ...rest } = options ?? {};

  return {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
}
