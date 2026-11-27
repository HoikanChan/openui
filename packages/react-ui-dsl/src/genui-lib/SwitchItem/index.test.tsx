// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SwitchItemView } from "./view";

describe("SwitchItem", () => {
  it("renders its label and initial switch state", () => {
    const { container } = render(
      <SwitchItemView defaultChecked label="Auto refresh" name="autoRefresh" />,
    );

    expect(container.textContent).toContain("Auto refresh");
    expect(container.querySelector('[role="switch"]')?.getAttribute("aria-checked")).toBe("true");
  });
});
