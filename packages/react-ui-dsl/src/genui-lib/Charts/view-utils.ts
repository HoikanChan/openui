import type * as echarts from "echarts";
import type { z } from "zod";
import type { SeriesSchema } from "./Series";
import type { ScatterSeriesSchema } from "./ScatterSeries";

type ElementLike = {
  type: "element";
  props: Record<string, unknown>;
};

type ChartDataset = { source: number[][] } | undefined;
type ChartOptions = (Omit<echarts.EChartsOption, "title"> & { title?: string }) | undefined;

function unwrapElement<T extends Record<string, unknown>>(value: T | ElementLike): T {
  return (typeof value === "object" &&
  value !== null &&
  "type" in value &&
  value.type === "element" &&
  "props" in value
    ? value.props
    : value) as T;
}

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
  series: (z.infer<typeof SeriesSchema> | ElementLike)[],
): { source: (string | number)[][] } {
  const header: (string | number)[] = ['category', ...labels];
  const rows = normalizeSeriesItems(series).map(s => [s.category, ...s.values] as (string | number)[]);
  return { source: [header, ...rows] };
}

export function normalizeSeriesItems(
  series: (z.infer<typeof SeriesSchema> | ElementLike)[],
): z.infer<typeof SeriesSchema>[] {
  return (series ?? []).map((value) => unwrapElement<z.infer<typeof SeriesSchema>>(value));
}

export function buildScatterSeries(
  datasets: (z.infer<typeof ScatterSeriesSchema> | ElementLike)[],
): echarts.SeriesOption[] {
  return datasets.map((rawDataset) => {
    const ds = unwrapElement<z.infer<typeof ScatterSeriesSchema>>(rawDataset);
    const points = (ds.points ?? []).map((rawPoint) =>
      unwrapElement(rawPoint as z.infer<typeof ScatterSeriesSchema>["points"][number] | ElementLike));

    return {
      type: "scatter" as const,
      name: ds.name,
      data: points.map((p) => [p.x, p.y, ...(p.z !== undefined ? [p.z] : [])]),
      ...(points.some((p) => p.z !== undefined)
      ? {
          symbolSize: (value: number[]) => {
            const z = value[2];
            return typeof z === "number" ? Math.max(8, Math.sqrt(Math.max(z, 0)) * 4) : 10;
          },
        }
      : {}),
    };
  });
}
