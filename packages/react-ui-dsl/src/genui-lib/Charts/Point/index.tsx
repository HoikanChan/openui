"use client";
import { defineComponent } from "@openuidev/react-lang";
import { z } from "zod";

export const PointSchema = z.object({
  x: z.number(),
  y: z.number(),
  z: z.number().optional(),
});

export const Point = defineComponent({
  name: "Point",
  props: PointSchema,
  description: "A data point with optional z dimension for bubble charts",
  component: () => null,
});
