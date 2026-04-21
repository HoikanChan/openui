import type * as echarts from "echarts";
import type { z } from "zod";
import type { SeriesSchema } from "./Series";
import type { ScatterSeriesSchema } from "./ScatterSeries";

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

export function buildDataset(
  labels: string[],
  series: z.infer<typeof SeriesSchema>[],
): { source: (string | number)[][] } {
  const header: (string | number)[] = ['category', ...labels];
  const rows = series.map(s => [s.category, ...s.values] as (string | number)[]);
  return { source: [header, ...rows] };
}

export function buildScatterSeries(
  datasets: z.infer<typeof ScatterSeriesSchema>[],
): echarts.SeriesOption[] {
  return datasets.map(ds => ({
    type: 'scatter' as const,
    name: ds.name,
    data: ds.points.map(p => [p.x, p.y, ...(p.z !== undefined ? [p.z] : [])]),
  }));
}
