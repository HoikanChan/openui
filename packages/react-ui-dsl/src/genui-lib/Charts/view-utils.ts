import type * as echarts from "echarts";
import type { z } from "zod";
import type { SeriesSchema } from "./Series";
import type { ScatterSeriesSchema } from "./ScatterSeries";

type ElementLike = {
  type: "element";
  props: Record<string, unknown>;
};

type ChartDataset = { source: number[][] } | undefined;
type ChartOptions =
  | echarts.EChartsOption
  | (Omit<echarts.EChartsOption, "title"> & { title?: string })
  | undefined;
export type MiniChartDatum = number | { value: number; label?: string };
export type MiniChartData = MiniChartDatum[];
export type NormalizedMiniChartDatum = { value: number; label: string };

const MINI_CHART_AUTO_HEIGHT_FALLBACK = 36;
const MINI_CHART_AUTO_HEIGHT_MIN = 24;
const MINI_CHART_AUTO_HEIGHT_MAX = 44;
const MINI_CHART_AUTO_HEIGHT_RATIO = 0.22;
const MINI_CHART_AUTO_WIDTH_MIN = 96;
const DEFAULT_CHART_LEGEND: echarts.LegendComponentOption = {
  orient: "horizontal",
  left: "center",
  bottom: 0,
};

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
  const { title, legend, ...rest } = options ?? {};

  return {
    ...rest,
    ...(legend ? { legend: { ...legend, ...DEFAULT_CHART_LEGEND } } : {}),
    ...(typeof title === "string" ? { title: { text: title } } : title ? { title } : {}),
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

export function normalizeMiniChartData(data: MiniChartData): NormalizedMiniChartDatum[] {
  return (data ?? []).map((item, index) =>
    typeof item === "number"
      ? { value: item, label: `Item ${index + 1}` }
      : { value: item.value, label: item.label ?? `Item ${index + 1}` });
}

export function getRecentMiniChartDataThatFits<T extends MiniChartData>(
  data: T,
  containerWidth: number,
  elementSpacing: number,
): T {
  if (containerWidth <= 0 || data.length === 0) {
    return data;
  }

  const maxItems = Math.max(1, Math.floor(containerWidth / elementSpacing));
  if (maxItems >= data.length) {
    return data;
  }

  return data.slice(-maxItems) as T;
}

export function getAutoMiniChartHeight(containerWidth: number): number {
  if (containerWidth <= 0) {
    return MINI_CHART_AUTO_HEIGHT_FALLBACK;
  }

  return Math.min(
    MINI_CHART_AUTO_HEIGHT_MAX,
    Math.max(MINI_CHART_AUTO_HEIGHT_MIN, Math.round(containerWidth * MINI_CHART_AUTO_HEIGHT_RATIO)),
  );
}

export function getAutoMiniChartWidth(
  dataLength: number,
  elementSpacing: number,
  containerWidth: number,
): number {
  const desiredWidth = Math.max(MINI_CHART_AUTO_WIDTH_MIN, dataLength * elementSpacing);

  if (containerWidth <= 0) {
    return desiredWidth;
  }

  return Math.min(containerWidth, desiredWidth);
}
