// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { buildChartOption } from "../components/chart/utils";
import { resolveButtonAppearance } from "./Button";
import { DescriptionsView, formatDescriptionValue, resolveAutoSpan } from "./Descriptions";
import { resolveTagAppearance, TagView } from "./Tag";
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

  it("maps DSL tag props to deterministic antd appearance", () => {
    expect(resolveTagAppearance({ size: "sm", variant: "danger" })).toEqual({
      color: "error",
      style: { fontSize: 12, lineHeight: "18px", paddingInline: 7, paddingBlock: 0 },
    });

    expect(resolveTagAppearance({ size: "lg", variant: "info" })).toEqual({
      color: "processing",
      style: { fontSize: 16, lineHeight: "26px", paddingInline: 13, paddingBlock: 2 },
    });
  });

  it("keeps icon as an explicit token without rendering an implicit icon graphic", () => {
    const element = TagView({
      icon: "alert-circle",
      size: "md",
      text: "Escalated",
      variant: "warning",
    });

    expect(element.props["data-icon-token"]).toBe("alert-circle");
    expect(element.props.children).toBe("Escalated");
  });

  it("computes auto span for long metric-card values", () => {
    expect(resolveAutoSpan("OK", 220)).toBe(1);
    expect(resolveAutoSpan("this-is-a-very-long-value-that-should-span-multiple-columns", 140)).toBeGreaterThan(1);
  });

  it("keeps component values untouched during formatting", () => {
    const tagValue = <TagView text="Active" variant="success" />;
    expect(formatDescriptionValue(tagValue, "date")).toBe(tagValue);
  });

  it("renders descriptions cards with title and metric-card styling hooks", () => {
    const element = DescriptionsView({
      title: "Profile",
      columns: 3,
      items: [
        { kind: "field", label: "Name", renderedValue: "Alice", resolvedSpan: 1 },
        { kind: "field", label: "Status", renderedValue: <TagView text="Active" variant="success" />, resolvedSpan: 1 },
      ],
    });

    expect(element.props["data-descriptions-columns"]).toBe(3);
    expect(element.props["data-descriptions-variant"]).toBe("bordered");
    expect(element.props.children[0].props.children[0].props.children).toBe("Profile");
  });

  it("renders a plain minimal layout when border is false", () => {
    const element = DescriptionsView({
      title: "User Info",
      border: false,
      columns: 4,
      items: [
        { kind: "field", label: "UserName", renderedValue: "Zhou Maomao", resolvedSpan: 1 },
        { kind: "field", label: "Telephone", renderedValue: "1810000000", resolvedSpan: 1 },
      ],
    });

    expect(element.props["data-descriptions-variant"]).toBe("plain");
    const content = element.props.children[1];
    expect(content.props.children[0].props["data-descriptions-layout"]).toBe("plain");
  });

  it("renders consecutive top-level fields into a shared grid section", () => {
    const { container } = render(
      DescriptionsView({
        title: "Profile",
        columns: 3,
        items: [
          { kind: "field", label: "Name", renderedValue: "Alice", resolvedSpan: 1 },
          { kind: "field", label: "Email", renderedValue: "alice@example.com", resolvedSpan: 2 },
        ],
      }),
    );

    const grids = container.querySelectorAll('[data-descriptions-layout="bordered"]');
    const rows = container.querySelectorAll('[data-descriptions-row="bordered"]');

    expect(grids).toHaveLength(1);
    expect(rows).toHaveLength(1);
    expect(rows[0]?.textContent).toContain("Name");
    expect(rows[0]?.textContent).toContain("Email");
  });

  it("strips default list bullets from description field content", () => {
    const { container } = render(
      DescriptionsView({
        title: "Summary",
        items: [
          {
            kind: "field",
            label: "Stats",
            renderedValue: (
              <ul>
                <li>NE-01-Core-Switch GigabitEthernet0/0/1</li>
                <li>NE-02-Access-Router Ethernet1/1</li>
              </ul>
            ),
            resolvedSpan: 3,
          },
        ],
      }),
    );

    const list = container.querySelector("ul");
    const item = container.querySelector("li");

    expect(list?.getAttribute("style")).toContain("list-style: none");
    expect(list?.getAttribute("style")).toContain("padding-inline-start: 0");
    expect(item?.getAttribute("style")).toContain("list-style: none");
  });

  it("packs bordered rows deterministically when a wide field does not fit the current row", () => {
    const { container } = render(
      DescriptionsView({
        title: "Configuration",
        columns: 2,
        items: [
          { kind: "field", label: "Time", renderedValue: "18:00:00", resolvedSpan: 1 },
          {
            kind: "field",
            label: "Config Info",
            renderedValue: (
              <ul>
                <li>Data disk type: MongoDB</li>
                <li>Database version: 3.4</li>
              </ul>
            ),
            resolvedSpan: 2,
          },
          { kind: "field", label: "Status", renderedValue: <TagView text="Running" variant="success" />, resolvedSpan: 1 },
          { kind: "field", label: "Discount", renderedValue: "$20.00", resolvedSpan: 1 },
        ],
      }),
    );

    const rows = Array.from(container.querySelectorAll('[data-descriptions-row="bordered"]'));

    expect(rows).toHaveLength(3);
    expect(rows[0]?.textContent).toContain("Time");
    expect(rows[0]?.textContent).toContain("18:00:00");
    expect(rows[0]?.textContent).not.toContain("Config Info");
    expect(rows[1]?.textContent).toContain("Config Info");
    expect(rows[1]?.textContent).toContain("Data disk type: MongoDB");
    expect(rows[2]?.textContent).toContain("Status");
    expect(rows[2]?.textContent).toContain("Running");
    expect(rows[2]?.textContent).toContain("Discount");
    expect(rows[2]?.textContent).toContain("$20.00");
  });
});
