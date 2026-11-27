"use client";

import { Slider, Typography } from "antd";

export type SliderVariant = "continuous" | "discrete";

export type SliderViewProps = {
  variant: SliderVariant;
  min: number;
  max: number;
  defaultValue?: number[];
  label?: string;
};

export function SliderView({ variant, min, max, defaultValue, label }: SliderViewProps) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4, width: "100%" }}>
      {label ? <Typography.Text>{label}</Typography.Text> : null}
      <Slider
        defaultValue={defaultValue}
        max={max}
        min={min}
        range
        step={variant === "discrete" ? 1 : undefined}
      />
    </div>
  );
}
