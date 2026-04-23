import { z } from "zod";

export const FlexPropsSchema = z.object({
  direction: z.enum(["row", "column"]).optional(),
  gap: z.enum(["none", "xs", "s", "m", "l", "xl", "2xl"]).optional(),
  align: z.enum(["start", "center", "end", "stretch", "baseline"]).optional(),
  justify: z.enum(["start", "center", "end", "between", "around", "evenly"]).optional(),
  wrap: z.boolean().optional(),
});

export const gapPixelMap = {
  none: 0,
  xs: 6,
  s: 8,
  m: 12,
  l: 18,
  xl: 24,
  "2xl": 36,
} as const;

export type FlexGapToken = keyof typeof gapPixelMap;

export const alignMap: Record<string, string> = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  stretch: "stretch",
  baseline: "baseline",
};

export const justifyMap: Record<string, string> = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  between: "space-between",
  around: "space-around",
  evenly: "space-evenly",
};

export function resolveGapPixels(gap?: FlexGapToken): number {
  return gap ? gapPixelMap[gap] : gapPixelMap["m"];
}
