"use client";

import { createLibrary } from "@openuidev/react-lang";
import type { Library, PromptOptions } from "@openuidev/react-lang";
import {
  AreaChart,
  BarChart,
  GaugeChart,
  HeatmapChart,
  HorizontalBarChart,
  LineChart,
  MiniChart,
  PieChart,
  Point,
  RadarChart,
  ScatterChart,
  ScatterSeries,
  Series,
  TreeMapChart,
} from "./Charts";
import { Button } from "./Button";
import { Card } from "./Card";
import { CardHeader } from "./CardHeader";
import { DescField, DescGroup, Descriptions } from "./Descriptions";
import { Form } from "./Form";
import { HLayout } from "./HLayout";
import { Image } from "./Image";
import { Link } from "./Link";
import { List } from "./List";
import { Separator } from "./Separator";
import { Select } from "./Select";
import { Tag } from "./Tag";
import { Col, Table } from "./Table";
import { Text } from "./Text";
import { TimeLine } from "./TimeLine";
import { Tabs } from "./Tabs";
import { VLayout } from "./VLayout";

const DEFAULT_PROMPT_ADDITIONAL_RULES = [
  'For Table column options.cell, `@Render("v", expr)` receives the cell value as `v`.',
  'If the render body needs other fields from the row, use `@Render("v", "row", expr)`. Do not reference `row` unless you declared it as the second binder.',
  "If a table cell label must combine the current value with another field from the same row, keep that logic inside a single `@Render(\"v\", \"row\", ...)` expression.",
  "Use `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration` for display formatting.",
  "Never use the removed `format` prop on `Col` or `DescField`.",
  "Use Descriptions for single-record detail views instead of Table.",
  "Accessing a field on an array extracts that field from every element: `filteredRows.fieldName` returns an array of that field's values. Use this to build Series data from filtered row sets.",
  "Never hardcode data values from the data model. Always reference fields via data paths or derived variables.",
  "For positional tuple arrays such as `[timestamp, value]`, project the needed tuple index before formatting; for example use `@Each(data.samples, \"item\", @FormatDate(item[0], \"dateTime\"))` for labels and `@Each(data.samples, \"item\", item[1])` for values.",
  "Byte-count fields such as `bytes`, `*Bytes`, `inBytes`, `outBytes`, `totalBytes`, `usedBytes`, or rows with `unit: \"bytes\"` should display through `@FormatBytes` instead of raw integers.",
  "Rate fields such as `bandwidth`, `bps`, `bitrate`, or `bitsPerSecond` are throughput/capacity rates, not byte counts. Do not use `@FormatBytes` for them; format bits-per-second values as Mbps or Gbps with `@FormatNumber` and a unit suffix.",
  "Do not compute utilization percentages by dividing cumulative byte totals by bandwidth or bitrate fields unless the data provides a matching time window or already exposes utilization as a ratio.",
  "Ratios derived from fields such as `used / total`, `active / total`, or `usedBytes / totalBytes` should display through `@FormatPercent` in text, table cells, and descriptions.",
  "When derived row-level displays need multiple fields from the same row, use `@Render(\"v\", \"row\", ...)` so every referenced field is in scope.",
  "Do not declare pseudo-reusable component templates that reference undeclared variables. Repeated displays should use existing components directly, valid `@Each` templates, table cells, or descriptions.",
  "For ordinary homogeneous record lists, prefer one clear Table with formatted columns; avoid duplicating the same record set in summary cards plus a full table unless the data exposes distinct summary values.",
  "Do not add a chart that repeats the same homogeneous records already shown in a Table by pairing record names with one numeric field. Use charts when the data is explicitly chart-ready or when a trend/distribution/composition is the primary user request.",
  "When host data is provided, never invent, backfill, or synthesize labels, rows, metrics, statuses, timestamps, percentages, details, categories, or records that are absent from the data model.",
  "For null-dominant records where most fields are null, missing, or unavailable, preserve real non-null fields and show missing fields with `No data`, `Unknown`, `Not available`, `?`, or omit them.",
  "For null-dominant records, prefer `Descriptions` for a single object and direct `Table` columns for arrays of records. Never synthesize rows, metrics, statuses, timestamps, percentages, details, or KPI cards to make the UI look complete.",
  "Use nullish coalescing for missing-value display fallbacks, for example `data.file ?? \"No data\"` or `@Render(\"v\", Text(v ?? \"No data\"))`.",
  "For dynamic-key object maps such as `{ \"dev-001\": {...}, \"dev-002\": {...} }`, use `@ObjectEntries(...)` or `@ObjectKeys(...)` instead of hardcoding sample keys.",
  "If timeline rows already expose `title`, `description`, and `status`, pass them directly to `TimeLine(data.timeline.items, data.timeline.title)`.",
  "Only use chart components when the data model already exposes chart-ready fields that match the component signature.",
  "Treat primitive numeric arrays (`number[]`) as compact quantitative series that should be visualized by default, not rendered as raw JSON, comma-separated text, nested tables, or index-value tables.",
  "Primitive numeric arrays are chart-ready for MiniChart. MiniChart accepts `number[]` directly and derives compact point labels automatically; do not invent labels, series, categories, indexes, bins, or unsupported helper functions just to use a full chart.",
  "When a `number[]` appears as a field inside records shown in a Table, render it row-locally with Col(..., {cell: @Render(\"v\", MiniChart(..., v))}); never combine arrays from multiple rows into one chart unless the data explicitly models one shared series.",
  "Choose MiniChart type from semantics when available: use `line` for ordered sequences such as trend, sparkline, history, timeline, series, usage, or readings; use `bar` for samples, measurements, durations, latencies, scores, counts, buckets, or distribution-like arrays. If semantics are unclear, prefer `line` for ordered/time-like data and `bar` for sample/distribution-like data.",
  "When a single object contains scalar summary fields plus one or more `number[]` fields, show scalar fields with Descriptions and render each `number[]` field as a nearby MiniChart.",
  "For standalone numeric arrays rendered with MiniChart, keep the chart compact: place it directly near the scalar summary or inside the same content group, avoid wrapping a MiniChart alone in a large separate Card, and omit height unless the user explicitly asks for a large chart.",
  "Never set MiniChart height above 96 for a primitive numeric array; default to omitting height so MiniChart can auto-size compactly.",
  "Include scalar identifier and context fields such as id, name, title, endpoint, label, unit, count, current, min, max, avg, p95, p99, total, and sampleCount alongside nearby MiniChart visualizations.",
  "Do not drop scalar context around numeric arrays. Pair the MiniChart with available identifiers, names, units, latest/current/min/max/p95/avg/count fields when present.",
  "Do not invent labels, series, categories, or missing time points from raw rows, statistics, or time ranges just to make a chart render.",
  "If the data model only contains raw row records, prefer Table or Descriptions instead of fabricating chart props.",
  "MiniChart is a compact single-series trend primitive for KPI cards, table cells, row-local numeric arrays, and dense summaries. Use it only with existing single-series numeric data.",
  "MiniChart always fills the available width. Omit MiniChart height unless the layout needs a tighter or taller trend.",
];

const DEFAULT_PROMPT_EXAMPLES = [
  `root = VLayout([employeeTable])
employeeTable = Table([nameCol, salaryCol, joinedCol, statusCol], data.employees)
nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})
salaryCol = Col("Salary", "salary")
joinedCol = Col("Joined", "joinedAt", {cell: @Render("v", Text(@FormatDate(v, "date")))})
statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": Text("Active"), "0": Text("Inactive")}, Text("Unknown")))})`,
  `root = VLayout([ordersTable])
ordersTable = Table([idCol, statusCol], data.orders)
idCol = Col("Order ID", "id")
statusCol = Col("Status", "status", {cell: @Render("v", "row", Text(row.id + ": " + @Switch(v, {"paid": "Paid", "pending": "Pending"}, "Unknown")))})`,
  `root = VLayout([latencyChart])
timestampLabels = @Each(data.samples, "item", @FormatDate(item[0], "dateTime"))
latencyValues = @Each(data.samples, "item", item[1])
latencySeries = Series("Latency (ms)", latencyValues)
latencyChart = LineChart(timestampLabels, [latencySeries], "smooth", "Time", "Latency (ms)")`,
  `root = VLayout([volumeTable])
volumeTable = Table([nameCol, totalCol, usedCol, usageCol], data.volumes)
nameCol = Col("Volume", "name")
totalCol = Col("Total", "totalBytes", {cell: @Render("v", Text(@FormatBytes(v)))})
usedCol = Col("Used", "usedBytes", {cell: @Render("v", Text(@FormatBytes(v)))})
usageCol = Col("Usage", "usedBytes", {cell: @Render("v", "row", Text(@FormatPercent(row.usedBytes / row.totalBytes, 1)))})`,
  `root = VLayout([linkTable])
linkTable = Table([nameCol, trafficCol, bandwidthCol], data.links)
nameCol = Col("Link", "name")
trafficCol = Col("Traffic", "inBytes", {cell: @Render("v", "row", Text(@FormatBytes(row.inBytes + row.outBytes)))})
bandwidthCol = Col("Bandwidth", "bandwidth", {cell: @Render("v", Text(v >= 1000000000 ? @FormatNumber(v / 1000000000, 1) + " Gbps" : @FormatNumber(v / 1000000, 1) + " Mbps"))})`,
  `root = VLayout([detail])
detail = Descriptions([DescField("Name", data.user.name), DescField("Email", data.user.email), account], "Profile")
account = DescGroup("Account", [DescField("Status", Tag(data.user.status, "success")), DescField("Joined", @FormatDate(data.user.joinedAt, "dateTime"), 2)], 2)`,
  `root = VLayout([timelineComponent])
timelineComponent = TimeLine(data.timeline.items, data.timeline.title)`,
  `root = VLayout([header, trendChart])
header = Text("Bandwidth Utilization Trend", "large")
ne01Rows = Filter(data.rows, "portResId", "==", data.statistics[0].portResId)
ne02Rows = Filter(data.rows, "portResId", "==", data.statistics[1].portResId)
ne01Series = Series(data.statistics[0].deviceName + " " + data.statistics[0].showName, ne01Rows.PeakBandwidthUtilization)
ne02Series = Series(data.statistics[1].deviceName + " " + data.statistics[1].showName, ne02Rows.PeakBandwidthUtilization)
timeLabels = @FormatDate(ne01Rows.time, "YYYY-MM-DD HH:mm")
trendChart = LineChart(timeLabels, [ne01Series, ne02Series], "smooth", "Time", "Peak Bandwidth Utilization (%)")`,
  `root = VLayout([rawRowsTitle, rawRowsTable])
rawRowsTitle = Text("Bandwidth Utilization Records", "large")
rawRowsTable = Table([deviceCol, interfaceCol, timeCol, utilizationCol], data.rows)
deviceCol = Col("Device", "deviceName")
interfaceCol = Col("Interface", "showName")
timeCol = Col("Time", "time")
utilizationCol = Col("Peak Utilization", "PeakBandwidthUtilization")`,
  `root = VLayout([deviceTable])
deviceRows = @ObjectEntries(data.devicesById)
deviceTable = Table([deviceKeyCol, statusCol], deviceRows)
deviceKeyCol = Col("Device", "key")
statusCol = Col("Status", "value.status")`,
  `root = VLayout([kpiCard])
kpiCard = Card([cardTitle, cardTrend], "card", "standard")
cardTitle = Text("7-Day Latency Trend", "large")
cardTrend = MiniChart("line", data.metrics.sparkline)`,
  `root = VLayout([itemsTable])
itemsTable = Table([nameCol, currentCol, valuesCol], data.items)
nameCol = Col("Name", "name")
currentCol = Col("Current", "current", {cell: @Render("v", Text(@FormatNumber(v, 1)))})
valuesCol = Col("Values", "values", {cell: @Render("v", MiniChart("line", v))})`,
  `root = VLayout([summary, measurementsTitle, measurementsChart])
summary = Descriptions([DescField("Name", data.summary.name), DescField("Count", data.summary.count), DescField("Average", @FormatNumber(data.summary.avg, 1))], "Summary")
measurementsTitle = Text("Measurements", "large")
measurementsChart = MiniChart("bar", data.summary.measurements, 96)`,
  `root = VLayout([recordDetail])
recordDetail = Descriptions([DescField("ID", data.record.id ?? "No data"), DescField("Name", data.record.name ?? "No data"), DescField("File", data.record.file ?? "No data"), DescField("Status", data.record.status ?? "No data"), DescField("Metric", data.record.metric ?? "No data")], "Record")`,
];

function mergePromptOptions(options?: PromptOptions): PromptOptions {
  return {
    ...options,
    additionalRules: [...DEFAULT_PROMPT_ADDITIONAL_RULES, ...(options?.additionalRules ?? [])],
    examples: [...DEFAULT_PROMPT_EXAMPLES, ...(options?.examples ?? [])],
  };
}

const baseDslLibrary = createLibrary({
  root: "VLayout",
  components: [
    VLayout,
    HLayout,
    Text,
    Button,
    Select,
    Separator,
    Tag,
    Image,
    Link,
    Card,
    CardHeader,
    Descriptions,
    DescGroup,
    DescField,
    List,
    Form,
    Col,
    Table,
    PieChart,
    LineChart,
    BarChart,
    GaugeChart,
    HorizontalBarChart,
    AreaChart,
    RadarChart,
    HeatmapChart,
    TreeMapChart,
    ScatterChart,
    MiniChart,
    Series,
    ScatterSeries,
    Point,
    TimeLine,
    Tabs,
  ],
});

export const dslLibrary: Library = {
  ...baseDslLibrary,
  prompt(options?: PromptOptions): string {
    return baseDslLibrary.prompt(mergePromptOptions(options));
  },
};
