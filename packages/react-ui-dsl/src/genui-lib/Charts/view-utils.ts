import type * as echarts from "echarts";
import type { z } from "zod";
import type { ScatterSeriesSchema } from "./ScatterSeries";
import type { SeriesSchema } from "./Series";

type ElementLike = {
  type: "element";
  props: Record<string, unknown>;
};

type ChartDataset = { source: number[][] } | undefined;
type ChartOptions = echarts.EChartsOption | undefined;
const DEFAULT_CHART_LEGEND: echarts.LegendComponentOption = {
  orient: "horizontal",
  left: "center",
  bottom: 10,
};

// Axis names sit outside the tick labels, so containLabel alone cannot keep
// them on canvas. Center the name under the axis labels and reserve room for it.
const AXIS_NAME_GAP = 28;
const AXIS_NAME_GRID_BOTTOM = 56;

type CartesianAxis = echarts.XAXisComponentOption | echarts.YAXisComponentOption;

function hasAxisName(axis: CartesianAxis | CartesianAxis[] | undefined): boolean {
  if (!axis) return false;
  return Array.isArray(axis) ? axis.some((item) => Boolean(item.name)) : Boolean(axis.name);
}

function centerAxisName<T extends CartesianAxis>(axis: T): T {
  return axis.name ? { ...axis, nameLocation: "middle", nameGap: AXIS_NAME_GAP } : axis;
}

function centerAxisNames<T extends CartesianAxis>(axis: T | T[] | undefined): T | T[] | undefined {
  if (!axis) return axis;
  return Array.isArray(axis) ? axis.map((item) => centerAxisName(item)) : centerAxisName(axis);
}

function unwrapElement<T extends Record<string, unknown>>(value: T | ElementLike): T {
  return (
    typeof value === "object" &&
    value !== null &&
    "type" in value &&
    value.type === "element" &&
    "props" in value
      ? value.props
      : value
  ) as T;
}

export function buildChartOption(
  options?: ChartOptions,
  data?: ChartDataset,
): echarts.EChartsOption {
  const source: ChartOptions = options ?? {};
  const { title, legend, xAxis, yAxis, grid, ...rest } = source;
  const isCartesian = Boolean(xAxis || yAxis);
  const gridOption =
    grid ??
    (isCartesian
      ? {
          containLabel: true,
          ...(hasAxisName(xAxis) ? { bottom: AXIS_NAME_GRID_BOTTOM } : {}),
        }
      : undefined);

  return {
    ...rest,
    ...(xAxis ? { xAxis: centerAxisNames(xAxis) } : {}),
    ...(yAxis ? { yAxis: centerAxisNames(yAxis) } : {}),
    ...(gridOption ? { grid: gridOption } : {}),
    ...(legend ? { legend: { ...legend, ...DEFAULT_CHART_LEGEND } } : {}),
    ...(typeof title === "string" ? { title: { text: title } } : title ? { title } : {}),
    ...(data ? { dataset: { source: data.source } } : {}),
  };
}

export function buildDataset(
  labels: string[],
  series: (z.infer<typeof SeriesSchema> | ElementLike)[],
): { source: (string | number)[][] } {
  const header: (string | number)[] = ["category", ...labels];
  const rows = normalizeSeriesItems(series).map(
    (s) => [s.category, ...s.values] as (string | number)[],
  );
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
      unwrapElement(
        rawPoint as z.infer<typeof ScatterSeriesSchema>["points"][number] | ElementLike,
      ),
    );

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
