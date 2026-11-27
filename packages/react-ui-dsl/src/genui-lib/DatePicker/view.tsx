"use client";

import { DatePicker } from "antd";

export type DatePickerMode = "single" | "range";

export type DatePickerViewProps = {
  mode?: DatePickerMode;
};

const pickerStyle = { width: "100%" } as const;

export function DatePickerView({ mode = "single" }: DatePickerViewProps) {
  switch (mode) {
    case "range":
      return <DatePicker.RangePicker style={pickerStyle} />;
    case "single":
      return <DatePicker style={pickerStyle} />;
    default: {
      const exhaustive: never = mode;
      return exhaustive;
    }
  }
}
