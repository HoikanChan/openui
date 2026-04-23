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
import { Form } from "./Form";
import { HLayout } from "./HLayout";
import { Image } from "./Image";
import { Link } from "./Link";
import { List } from "./List";
import { Select } from "./Select";
import { Col, Table } from "./Table";
import { Text } from "./Text";
import { TimeLine } from "./TimeLine";
import { Tabs } from "./Tabs";
import { VLayout } from "./VLayout";

const DEFAULT_PROMPT_ADDITIONAL_RULES = [
  'For Table column options.cell, `@Render("v", expr)` receives the cell value as `v`.',
  'If the render body needs other fields from the row, use `@Render("v", "row", expr)`. Do not reference `row` unless you declared it as the second binder.',
  "Use `format` only for ISO date/time string fields, never for numeric fields like salary or revenue.",
];

const DEFAULT_PROMPT_EXAMPLES = [
  `root = VLayout([employeeTable])
employeeTable = Table([nameCol, salaryCol, joinedCol, statusCol], data.employees)
nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})
salaryCol = Col("Salary", "salary")
joinedCol = Col("Joined", "joinedAt", {format: "date"})
statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": Text("Active"), "0": Text("Inactive")}, Text("Unknown")))})`,
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
    Image,
    Link,
    Card,
    CardHeader,
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
