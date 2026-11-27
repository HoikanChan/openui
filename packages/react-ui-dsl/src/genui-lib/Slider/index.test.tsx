// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SliderView } from "./view";

describe("Slider", () => {
  it("renders the label and a range slider bounded by min and max", () => {
    const { container } = render(
      <SliderView defaultValue={[20, 60]} label="Opacity" max={100} min={0} variant="continuous" />,
    );

    expect(container.textContent).toContain("Opacity");
    expect(container.querySelectorAll('[role="slider"]')).toHaveLength(2);
  });
});
