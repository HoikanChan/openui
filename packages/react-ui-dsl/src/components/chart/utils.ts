import type * as echarts from "echarts";

type ChartProperties = Omit<echarts.EChartsOption, "title"> & { title?: string };
type ChartData = { source: number[][] };

export function buildChartOption(
  properties?: ChartProperties,
  data?: ChartData,
): echarts.EChartsOption {
  const { title, ...rest } = properties ?? {};

  return {
    ...rest,
    ...(title ? { title: { text: title } } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
}
