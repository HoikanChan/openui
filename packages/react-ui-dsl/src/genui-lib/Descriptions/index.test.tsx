// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createParser } from "../../../../lang-core/src";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../dslLibrary";
import { formatDescriptionValue, resolveAutoSpan, resolveDescriptionFieldValue } from "./index";

describe("react-ui-dsl Descriptions schema", () => {
  it("parses descriptions with mixed fields and groups", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Descriptions([nameField, accountGroup], "Profile", Tag("Ready", "success"), 3)
nameField = DescField("Name", "Alice")
accountGroup = DescGroup("Account", [emailField, statusField], 2)
emailField = DescField("Email", "alice@example.com")
statusField = DescField("Status", Tag("Active", "success"))`);

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.typeName).toBe("Descriptions");
    expect(result.root?.props.items).toHaveLength(2);
    expect(result.root?.props.items[0]).toMatchObject({
      typeName: "DescField",
      props: { label: "Name", value: "Alice" },
    });
    expect(result.root?.props.items[1]).toMatchObject({
      typeName: "DescGroup",
      props: { title: "Account", columns: 2 },
    });
  });

  it("formats plain values while preserving component values", () => {
    expect(formatDescriptionValue("2026-01-02T03:04:05.000Z", "dateTime")).toContain("2026");

    const tagNode = <span data-chip="true">Active</span>;
    expect(formatDescriptionValue(tagNode, "date")).toBe(tagNode);
  });

  it("auto-expands long text to wider spans when no explicit span is provided", () => {
    expect(resolveAutoSpan("short", 220)).toBe(1);
    expect(resolveAutoSpan("this-email-address-is-way-too-long@example.com", 180)).toBeGreaterThan(1);
  });

  it("renders DSL element values through renderNode instead of stringifying them", () => {
    const tagNode = {
      type: "element" as const,
      typeName: "Tag",
      partial: false,
      props: { text: "Active", variant: "success" },
    };

    const result = resolveDescriptionFieldValue(
      { label: "Status", value: tagNode },
      (value) => <span data-rendered={String((value as any).props.text)}>{String((value as any).props.text)}</span>,
      1,
    );

    expect(result.renderedValue.props["data-rendered"]).toBe("Active");
    expect(result.resolvedSpan).toBe(1);
  });

  it("renders grouped field nodes with their labels and values", () => {
    const dsl = `root = Descriptions([accountGroup], "Profile")
accountGroup = DescGroup("Account", [statusField, roleField], 2)
statusField = DescField("Status", Tag("Active", "success"))
roleField = DescField("Role", "Administrator")`;

    const { container } = render(<Renderer library={dslLibrary} response={dsl} dataModel={{}} />);

    expect(container.innerHTML).toContain("Account");
    expect(container.innerHTML).toContain("Status");
    expect(container.innerHTML).toContain("Active");
    expect(container.innerHTML).toContain("Role");
    expect(container.innerHTML).toContain("Administrator");
  });
});
