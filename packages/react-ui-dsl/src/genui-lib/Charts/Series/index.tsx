"use client";
import { defineComponent } from "@openuidev/react-lang";
import { z } from "zod";

export const SeriesSchema = z.object({
  category: z.string(),
  values: z.array(z.number()),
});

export const Series = defineComponent({
  name: "Series",
  props: SeriesSchema,
  description: "A named data series for multi-series charts",
  component: () => null,
});
