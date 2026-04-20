import React from "react";
import { describe, expect, it } from "vitest";
import { buildChartOption } from "../components/chart/utils";
import { resolveButtonAppearance } from "./Button";
import { formatCell } from "./Table";
import { buildTimelineItems } from "./TimeLine";

describe("react-ui-dsl view layer helpers", () => {
  it("maps DSL button props to antd button appearance", () => {
    expect(resolveButtonAppearance({ status: "primary" })).toEqual({
      antType: "primary",
      danger: false,
    });

    expect(resolveButtonAppearance({ status: "risk" })).toEqual({
      antType: "default",
      danger: true,
    });

    expect(resolveButtonAppearance({ type: "text", status: "risk" })).toEqual({
      antType: "text",
      danger: true,
    });
  });

  it("formats date and time cells consistently", () => {
    expect(formatCell("not-a-date", "date")).toBe("not-a-date");
    expect(formatCell(null, "date")).toBe("");
    expect(formatCell("2026-01-02T03:04:05.000Z", "date")).toBeTruthy();
    expect(formatCell("2026-01-02T03:04:05.000Z", "dateTime")).toContain("2026");
  });

  it("builds chart options from title and dataset inputs", () => {
    expect(
      buildChartOption(
        {
          title: "Revenue",
          xAxis: { type: "category" },
          yAxis: { type: "value" },
        },
        { source: [["month", "value"], ["Jan", 10]] },
      ),
    ).toMatchObject({
      title: { text: "Revenue" },
      dataset: { source: [["month", "value"], ["Jan", 10]] },
      xAxis: { type: "category" },
      yAxis: { type: "value" },
    });
  });

  it("builds timeline items with rendered children", () => {
    const items = buildTimelineItems(
      [
        {
          iconType: "success",
          content: {
            title: "Deployed",
            children: ["ok"],
          },
        },
      ],
      (value) => <span data-value={String(value)}>{String(value)}</span>,
    );

    expect(items).toHaveLength(1);
    expect(items[0].color).toBe("green");
    expect(items[0].children.props.children[0].props.children).toBe("Deployed");
  });
});
