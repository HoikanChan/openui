"use client";

import { createLibrary } from "@openuidev/react-lang";
import type { ComponentGroup, Library } from "@openuidev/react-lang";
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
import { Image } from "./Image";
import { Input } from "./Input";
import { Link } from "./Link";
import { List } from "./List";
import { Separator } from "./Separator";
import { Select } from "./Select";
import { Tag } from "./Tag";
import { TagBlock } from "./TagBlock";
import { Col, Table } from "./Table";
import { TextContent } from "./TextContent";
import { MarkDownRenderer } from "./MarkDownRenderer";
import { TimeLine } from "./TimeLine";
import { Tabs } from "./Tabs";
import { Stack } from "./Stack";
import { PACKAGE_NAME, PACKAGE_VERSION } from "./packageMetadata";

// ── Component Groups ──
//
// Per-group `notes` are inlined into the `## Component Signatures` section of
// the generated prompt, right next to the signatures they constrain. Prefer
// putting component-specific guidance here over the global rules below.

export const dslComponentGroups: ComponentGroup[] = [
  {
    name: "Layout",
    components: ["Stack", "Separator", "Tabs"],
    notes: [
      '- For grid-like layouts, use Stack with direction "row" and wrap=true. Avoid justify="between" unless you specifically want large gutters.',
      "- Use nested Stacks when you need explicit rows/sections.",
      "- Use Tabs for alternative views (chart types, data sections) instead of inventing ternary show/hide patterns.",
    ],
  },
  {
    name: "Content",
    components: ["Card", "CardHeader", "TextContent", "MarkDownRenderer", "Image", "Link"],
    notes: [
      "- Use MarkDownRenderer for long-form text with code blocks; use TextContent for short UI labels.",
      '- Use Cards to group related KPIs or sections. Stack with direction "row" for side-by-side layouts.',
      "- Wrap charts in a Card with CardHeader for titled sections.",
    ],
  },
  {
    name: "Tables",
    components: ["Table", "Col"],
    notes: [
      '- Cell renderer: `Col(title, field, {cell: @Render("v", expr)})` — `v` is the cell value.',
      '- When the renderer needs other row fields, declare the second binder: `@Render("v", "row", expr)`. Keep all logic that combines `v` and `row` inside that single template — do not reference `row` unless you declared it.',
      "- For long string columns (names, identifiers, paths, addresses), add `tooltip: true` to avoid awkward wrapping.",
      "- Prefer one clear Table with formatted columns over duplicating the same record set as both summary cards and a full table.",
      "- Do not add a chart that simply re-pairs record names with one numeric field from a Table. Use charts when the data is chart-ready or a trend/distribution is the user's request.",
      '- The `format` prop on `Col` was removed. Use `cell: @Render("v", ...)` with `@FormatX` helpers instead.',
    ],
  },
  {
    name: "Descriptions",
    components: ["Descriptions", "DescGroup", "DescField"],
    notes: [
      "- Prefer Descriptions for single-record detail views; reserve Table for arrays of records.",
      "- When Descriptions will contain 6+ fields, set `columns=2` to avoid crowding (long device names, ports, IPs need horizontal space).",
      "- For paginated envelopes (total/pageSize/pageIndex plus list/items/rows), show pagination metadata with Descriptions and render the array with Table. Do not add Prev/Next Buttons unless a Query/Action can actually change pages.",
      "- The `format` prop on `DescField` was removed. Wrap values with `@FormatX(...)` directly.",
    ],
  },
  {
    name: "Charts (2D)",
    components: [
      "LineChart",
      "BarChart",
      "AreaChart",
      "RadarChart",
      "HorizontalBarChart",
      "HeatmapChart",
      "TreeMapChart",
      "Series",
    ],
    notes: [
      "- Only use chart components when the data model exposes chart-ready fields matching the signature. For raw row records, prefer Table or Descriptions.",
      '- For positional tuple arrays like `[timestamp, value]`, project the index first: `@Each(data.samples, "item", @FormatDate(item[0], "dateTime"))` for labels, `@Each(data.samples, "item", item[1])` for values.',
      "- When a multi-series LineChart is built by partitioning a flat array (e.g. `@Filter` over an entity field), derive the labels axis from one of the partitioned slices, NOT the unpartitioned source — the unpartitioned array has length N × entityCount while each filtered series has only N values, which leaves the right side blank.",
      "- If per-entity timestamps do not align across slices, use ScatterChart with Point pairs (or one LineChart per entity) so each series carries its own X axis.",
      "- Do not invent labels, series, categories, or missing time points just to make a chart render.",
    ],
  },
  {
    name: "Charts (1D)",
    components: ["PieChart", "GaugeChart"],
    notes: [
      '- PieChart takes parallel labels[] and values[] arrays of NUMBERS (not objects): PieChart(["Low", "Med", "High"], [12, 28, 7], "donut").',
      "- GaugeChart auto-scales 0-1 decimal values to 0-100% display. Pass `min=0, max=1` for ratio fields (availabilityRate, avgCpuUtil); pass `min=0, max=100` for values already in percentage form.",
    ],
  },
  {
    name: "Charts (Compact)",
    components: ["MiniChart"],
    notes: [
      "- MiniChart is a single-series numeric trend primitive for KPI cards, table cells, and dense summaries. Accepts `number[]` directly and derives compact point labels automatically.",
      "- Treat primitive `number[]` as chart-ready — never render as JSON, comma-separated text, or index-value tables.",
      '- Inside a Table, render row-local arrays with `Col(..., {cell: @Render("v", MiniChart("line", v))})`. Never combine arrays from multiple rows into one chart unless the data explicitly models one shared series.',
      "- Choose `type` from semantics: `line` for ordered/time-like sequences (trend, sparkline, history, usage, readings); `bar` for samples, measurements, durations, latencies, scores, counts, buckets. When unclear, default `line` for ordered data and `bar` for sample-like data.",
      "- Keep MiniChart compact: never set `height` above 96 for a primitive numeric array; default to omitting `height`. Width always fills.",
      "- When a single object has scalar summary fields plus one or more `number[]` fields, show scalars with Descriptions and render each array as a nearby MiniChart.",
      "- Pair MiniChart with available scalar context (id, name, unit, count, current, min, max, avg, p95, p99, total, sampleCount) — do not drop identifiers and stats when present.",
    ],
  },
  {
    name: "Charts (Scatter)",
    components: ["ScatterChart", "ScatterSeries", "Point"],
    notes: [
      "- When X values are epoch millisecond timestamps, pass `xType='time'` (4th arg) so the X axis is formatted as dates instead of raw numbers.",
    ],
  },
  {
    name: "Forms",
    components: ["Form", "Input", "Select"],
  },
  {
    name: "Data Display",
    components: ["Tag", "TagBlock", "List", "TimeLine", "Button"],
    notes: [
      "- For boolean fields (active, enabled, status), use a ternary: `v ? Tag(\"Active\", \"success\") : Tag(\"Inactive\", \"danger\")`. `@Switch` matches string keys so `{true: ...}` won't match boolean values.",
      '- Color-mapped Tag (variant is the 2nd arg in dsl): Tag(v, v == "high" ? "danger" : v == "medium" ? "warning" : "neutral").',
      "- If timeline rows already expose `title`, `description`, and `status`, pass them directly: `TimeLine(data.timeline.items, data.timeline.title)`.",
    ],
  },
];

// ── Global rules (cross-cutting; not tied to a single component) ──

export const DEFAULT_PROMPT_ADDITIONAL_RULES = [
  "Never use JavaScript conversion constructors or APIs such as String(...), Number(...), Boolean(...), Date(...), or Math.*. Use string concatenation and openui-lang builtins (@FormatNumber, @FormatDate, @Round, @Floor, @Ceil) instead.",
  "Use openui-lang concatenation with `+` for display strings; do not use JavaScript conversion helpers.",
  'For Table column options.cell, `@Render("v", expr)` receives the cell value. If the render body needs other fields from the row, use `@Render("v", "row", expr)`. If a table cell label must combine the current value with another field from the same row, do all row-dependent logic inside that one @Render expression.',
  "Use @FormatDate, @FormatBytes, @FormatNumber, @FormatPercent, @FormatDuration for display formatting. Byte-count fields (`bytes`, `*Bytes`, `inBytes`, `outBytes`, `totalBytes`, `usedBytes`, or rows with `unit: \"bytes\"`) → @FormatBytes. Rate fields (`bandwidth`, `bps`, `bitrate`, `bitsPerSecond`) are bits-per-second, NOT bytes — format as Mbps/Gbps with @FormatNumber plus a unit suffix. Ratios like `used/total`, `usedBytes/totalBytes`, `active/total` → @FormatPercent. Do not divide cumulative byte totals by bandwidth/bitrate to fake utilization unless the data provides a matching time window.",
  "Use `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration` for display formatting. Never use the removed `format` prop on `Col` or `DescField`.",
  "For positional tuple arrays, project the needed tuple index before formatting. For example, derive timestampLabels with @Each before passing labels to charts.",
  "Rate fields such as `bandwidth`, `bps`, `bitrate`, or `bitsPerSecond` are rates, not byte totals. Do not compute utilization percentages by dividing cumulative byte totals by bandwidth or bitrate fields. Ratios derived from fields such as `used / total` should use @FormatPercent.",
  "Never hardcode data values from the data model — always reference fields via data paths or derived variables. Accessing a field on an array extracts that field from every element: `filteredRows.fieldName` returns an array of values, useful for building Series.",
  "When host data is provided, never invent, backfill, or synthesize labels, rows, metrics, statuses, timestamps, percentages, or records that are absent. For null-dominant records, preserve real non-null fields and show missing ones with `?? \"No data\"` (or `\"Unknown\"`, `\"?\"`, or omit). Use Descriptions for the single object and Table for arrays — never synthesize rows or KPI cards to make the UI look complete.",
  "Never synthesize rows, metrics, statuses, timestamps, percentages, details, labels, series, categories, or missing time points from absent host data.",
  "For dynamic-key object maps such as `{ \"dev-001\": {...}, \"dev-002\": {...} }`, use `@ObjectEntries(...)` or `@ObjectKeys(...)` instead of hardcoding sample keys.",
  "For paginated envelopes with total/pageSize/pageIndex plus list/items/rows, show pagination metadata with Descriptions and render the array with Table. Do not add previous/next Button controls unless a Query or Action can actually change pages.",
  "Use Descriptions for single-record detail views instead of Table.",
  "If timeline rows already expose `title`, `description`, and `status`, pass them directly to `TimeLine(data.timeline.items, data.timeline.title)`.",
  "Only use chart components when the data model already exposes chart-ready fields. If the data model only contains raw row records, prefer Table or Descriptions. Do not invent labels, series, categories, or missing time points from raw rows.",
  "Do not declare pseudo-reusable component templates that reference undeclared variables. Repeated displays should use existing components directly, valid `@Each` templates, table cells, or descriptions.",
  "Do not add a chart that repeats the same homogeneous records shown in a Table; prefer one clear Table with formatted columns.",
  "MiniChart is a compact single-series trend primitive for KPI cards, table cells, dense summaries, and standalone primitive numeric arrays.",
  "Treat primitive numeric arrays (`number[]`) as compact quantitative series. When a `number[]` appears as a field inside records shown in a Table, render it with `Col(..., {cell: @Render(\"v\", MiniChart(..., v))})`.",
  "MiniChart accepts `number[]` directly and derives compact point labels automatically. For standalone numeric arrays rendered with MiniChart, keep the chart compact. Omit MiniChart height unless the layout needs a tighter or taller trend. Never set MiniChart height above 96 for a primitive numeric array.",
  "Include scalar identifier and context fields such as id, name, title, endpoint, label, unit, count, current, min, max, avg, p95 when pairing MiniChart with related values.",
];

export const DSL_BASE_CONTRACT_VERSION = `${PACKAGE_NAME}@${PACKAGE_VERSION}`;

// ── Examples (8 core patterns) ──

export const DEFAULT_PROMPT_EXAMPLES = [
  // Table with Render binders, Switch, Link, FormatDate
  `root = Stack([employeeTable])
employeeTable = Table([nameCol, salaryCol, joinedCol, statusCol], data.employees)
nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})
salaryCol = Col("Salary", "salary")
joinedCol = Col("Joined", "joinedAt", {cell: @Render("v", TextContent(@FormatDate(v, "date")))})
statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": TextContent("Active"), "0": TextContent("Inactive")}, TextContent("Unknown")))})`,
  // Combine value + other row field in a single cell via @Render("v", "row", ...) — id + ": " + Switch
  `root = Stack([ordersTable])
ordersTable = Table([idCol, statusCol], data.orders)
idCol = Col("Order ID", "id")
statusCol = Col("Status", "status", {cell: @Render("v", "row", TextContent(row.id + ": " + @Switch(v, {"paid": "Paid", "pending": "Pending"}, "Unknown")))})`,
  // Pagination envelope: Descriptions + Table
  `root = Stack([paginationSummary, rowsTable])
paginationSummary = Descriptions([totalField, pageField, pageSizeField], "Pagination")
totalField = DescField("Total", data.total)
pageField = DescField("Page", data.pageIndex + " / " + @Ceil(data.total / data.pageSize))
pageSizeField = DescField("Page Size", data.pageSize)
rowsTable = Table([nameCol, statusCol], data.list)
nameCol = Col("Name", "name")
statusCol = Col("Status", "status")`,
  // Dynamic-key object map via ObjectEntries
  `root = Stack([deviceTable])
deviceRows = @ObjectEntries(data.devicesById)
deviceTable = Table([deviceKeyCol, statusCol], deviceRows)
deviceKeyCol = Col("Device", "key")
statusCol = Col("Status", "value.status")`,
  // Tuple labels projected before formatting
  `root = Stack([samplesChart])
timestampLabels = @Each(data.samples, "item", @FormatDate(item[0], "dateTime"))
sampleValues = @Each(data.samples, "item", item[1])
samplesSeries = Series("Samples", sampleValues)
samplesChart = LineChart(timestampLabels, [samplesSeries], "smooth", "Time", "Value")`,
  // Bytes + percent formatting with row binder
  `root = Stack([volumeTable])
volumeTable = Table([nameCol, totalCol, usedCol, usageCol], data.volumes)
nameCol = Col("Volume", "name")
totalCol = Col("Total", "totalBytes", {cell: @Render("v", TextContent(@FormatBytes(v)))})
usedCol = Col("Used", "usedBytes", {cell: @Render("v", TextContent(@FormatBytes(v)))})
usageCol = Col("Usage", "usedBytes", {cell: @Render("v", "row", TextContent(@FormatPercent(row.usedBytes / row.totalBytes, 1)))})`,
  // Rate formatting without fake utilization
  `root = Stack([rateTable])
rateTable = Table([nameCol, rateCol], data.links)
nameCol = Col("Name", "name")
rateCol = Col("Bandwidth", "bandwidth", {cell: @Render("v", TextContent(v >= 1000000000 ? @FormatNumber(v / 1000000000, 1) + " Gbps" : @FormatNumber(v / 1000000, 1) + " Mbps"))})`,
  // Multi-series LineChart with partitioned labels
  `root = Stack([trendChart])
groupASlice = @Filter(data.records, "groupKey", "==", "group-a")
groupBSlice = @Filter(data.records, "groupKey", "==", "group-b")
trendLabels = @FormatDate(groupASlice.timestamp, "dateTime")
groupASeries = Series("Group A", groupASlice.value)
groupBSeries = Series("Group B", groupBSlice.value)
trendChart = LineChart(trendLabels, [groupASeries, groupBSeries], "smooth", "Time", "Value")`,
  // Partition raw rows by a parallel statistics array; per-entity series + per-partition labels
  `root = Stack([header, trendChart])
header = TextContent("Bandwidth Utilization Trend", "large")
ne01Rows = @Filter(data.rows, "portResId", "==", data.statistics[0].portResId)
ne02Rows = @Filter(data.rows, "portResId", "==", data.statistics[1].portResId)
ne01Series = Series(data.statistics[0].deviceName + " " + data.statistics[0].showName, ne01Rows.PeakBandwidthUtilization)
ne02Series = Series(data.statistics[1].deviceName + " " + data.statistics[1].showName, ne02Rows.PeakBandwidthUtilization)
timeLabels = @FormatDate(ne01Rows.time, "YYYY-MM-DD HH:mm")
trendChart = LineChart(timeLabels, [ne01Series, ne02Series], "smooth", "Time", "Peak Bandwidth Utilization (%)")`,
  // Raw rows prefer Table
  `root = Stack([rawRowsTable])
rawRowsTable = Table([deviceCol, interfaceCol, timeCol, utilizationCol], data.rows)
deviceCol = Col("Device", "deviceName")
interfaceCol = Col("Interface", "showName")
timeCol = Col("Time", "time", {cell: @Render("v", TextContent(@FormatDate(v, "dateTime")))})
utilizationCol = Col("Peak Utilization", "PeakBandwidthUtilization", {cell: @Render("v", TextContent(@FormatPercent(v / 100, 1)))})`,
  // Descriptions + DescGroup with span/columns
  `root = Stack([detail])
detail = Descriptions([DescField("Name", data.user.name), DescField("Email", data.user.email), account], "Profile")
account = DescGroup("Account", [DescField("Status", Tag(data.user.status, "success")), DescField("Joined", @FormatDate(data.user.joinedAt, "dateTime"), 2)], 2)`,
  // Timeline direct host-data binding
  `root = Stack([timelineComponent])
timelineComponent = TimeLine(data.timeline.items, data.timeline.title)`,
  // KPI MiniChart in Card
  `root = Stack([kpiCard])
kpiCard = Card([cardTitle, cardTrend], "card", "standard")
cardTitle = TextContent("7-Day Latency Trend", "large")
cardTrend = MiniChart("line", data.metrics.sparkline)`,
  // Standalone measurements MiniChart
  `root = Stack([measurementsChart])
measurementsChart = MiniChart("bar", data.summary.measurements)`,
  // MiniChart row-local in Table cell
  `root = Stack([itemsTable])
itemsTable = Table([nameCol, currentCol, valuesCol], data.items)
nameCol = Col("Name", "name")
currentCol = Col("Current", "current", {cell: @Render("v", TextContent(@FormatNumber(v, 1)))})
valuesCol = Col("Values", "values", {cell: @Render("v", MiniChart("line", v))})`,
  // Null-dominant record with fallbacks
  `root = Stack([recordDetail])
recordDetail = Descriptions([DescField("ID", data.record.id ?? "No data"), DescField("Name", data.record.name ?? "No data"), DescField("File", data.record.file ?? "No data"), DescField("Status", data.record.status ?? "No data"), DescField("Metric", data.record.metric ?? "No data")], "Record")`,
];

export const dslLibrary: Library = createLibrary({
  root: "Stack",
  contractVersion: DSL_BASE_CONTRACT_VERSION,
  componentGroups: dslComponentGroups,
  additionalRules: DEFAULT_PROMPT_ADDITIONAL_RULES,
  examples: DEFAULT_PROMPT_EXAMPLES,
  components: [
    Stack,
    TextContent,
    MarkDownRenderer,
    Button,
    Input,
    Select,
    Separator,
    Tag,
    TagBlock,
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
