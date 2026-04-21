"use client";

import { createLibrary } from "@openuidev/react-lang";
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

export const dslLibrary = createLibrary({
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
