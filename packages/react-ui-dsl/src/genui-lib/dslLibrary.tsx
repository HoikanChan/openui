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
  "For dynamic-key object maps such as `{ \"dev-001\": {...}, \"dev-002\": {...} }`, use `@ObjectEntries(...)` or `@ObjectKeys(...)` instead of hardcoding sample keys.",
  "If timeline rows already expose `title`, `description`, and `status`, pass them directly to `TimeLine(data.timeline.items, data.timeline.title)`.",
  "Only use chart components when the data model already exposes chart-ready fields that match the component signature.",
  "Do not invent labels, series, categories, or missing time points from raw rows, statistics, or time ranges just to make a chart render.",
  "If the data model only contains raw row records, prefer Table or Descriptions instead of fabricating chart props.",
  "MiniChart is a compact single-series trend primitive for KPI cards and dense summaries. Use it only with existing single-series sparkline-style data.",
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
cardTrend = MiniChart("line", data.metrics.sparkline, 96, "#1677ff")`,
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
