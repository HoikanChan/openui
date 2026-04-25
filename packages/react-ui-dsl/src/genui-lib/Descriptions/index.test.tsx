// @vitest-environment jsdom
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createParser } from "../../../../lang-core/src";
import { Renderer } from "@openuidev/react-lang";
import { dslLibrary } from "../dslLibrary";
import { resolveAutoSpan, resolveDescriptionFieldValue } from "./index";

describe("react-ui-dsl Descriptions schema", () => {
  it("parses descriptions with the optional border toggle", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Descriptions([nameField], "Profile", null, 3, false)
nameField = DescField("Name", "Alice")`);

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.typeName).toBe("Descriptions");
    expect(result.root?.props.border).toBe(false);
  });

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

  it("renders formatted field values directly from @Format* expressions", () => {
    const createdAt = "2026-01-02T03:04:05.000Z";
    const dsl = `root = Descriptions([joinedField], "Profile")
joinedField = DescField("Joined", @FormatDate(data.user.createdAt, "dateTime"))`;

    const { container } = render(
      <Renderer
        library={dslLibrary}
        response={dsl}
        locale="en-US"
        dataModel={{ user: { createdAt } }}
      />,
    );

    expect(container.innerHTML).toContain(
      new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(createdAt)),
    );
  });

  it("rejects the removed DescField format argument", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(
      `root = Descriptions([joinedField], "Profile")
joinedField = DescField("Joined", "2026-01-02T03:04:05.000Z", 2, "dateTime")`,
    );

    expect(result.meta.errors.length).toBeGreaterThan(0);
    expect(JSON.stringify(result.meta.errors)).toContain("DescField takes 3 arg");
  });
});
