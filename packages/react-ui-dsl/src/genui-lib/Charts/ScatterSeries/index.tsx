"use client";
import { defineComponent } from "@openuidev/react-lang";
import { z } from "zod";
import { PointSchema } from "../Point";

export const ScatterSeriesSchema = z.object({
  name: z.string(),
  points: z.array(PointSchema),
});

export const ScatterSeries = defineComponent({
  name: "ScatterSeries",
  props: ScatterSeriesSchema,
  description: "A named scatter series with data points for ScatterChart",
  component: () => null,
});
