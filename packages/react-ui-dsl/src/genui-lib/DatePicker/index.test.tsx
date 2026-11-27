// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DatePickerView } from "./view";

describe("DatePicker", () => {
  it("renders a single date input by default", () => {
    const { container } = render(<DatePickerView />);
    expect(container.querySelectorAll("input")).toHaveLength(1);
  });

  it("renders two inputs in range mode", () => {
    const { container } = render(<DatePickerView mode="range" />);
    expect(container.querySelectorAll("input")).toHaveLength(2);
  });
});
