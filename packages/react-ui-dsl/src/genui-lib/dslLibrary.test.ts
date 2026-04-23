import { describe, expect, it } from "vitest";
import { dslLibrary } from "./dslLibrary";

describe("react-ui-dsl exported prompt and schema surface", () => {
  it("exposes flattened component signatures for representative components", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.Button.signature).toContain("Button(text?:");
    expect(spec.components.Button.signature).not.toContain("properties");
    expect(spec.components.Button.signature).not.toContain("style");
    expect(spec.components.Button.signature).not.toContain("actions");

    expect(spec.components.Text.signature).toContain("Text(text: string");
    expect(spec.components.Text.signature).toContain("size?:");
    expect(spec.components.Text.signature).not.toContain("properties");
    expect(spec.components.Text.signature).not.toContain("style");

    expect(spec.components.CardHeader.signature).toContain("CardHeader(title?:");
    expect(spec.components.CardHeader.signature).toContain("subtitle?:");

    expect(spec.components.Card.signature).toContain("direction?:");
    expect(spec.components.Card.signature).toContain("gap?:");
    expect(spec.components.Card.signature).not.toContain("style");
    expect(spec.components.Card.signature).not.toContain("actions");
    expect(spec.components.Card.signature).not.toContain("header");
    expect(spec.components.Card.signature).not.toContain("width");

    expect(spec.components.Table.signature).not.toContain("style");
    expect(spec.components.Tag.signature).toContain("Tag(text:");
    expect(spec.components.Tag.signature).toContain("icon?:");
    expect(spec.components.Tag.signature).toContain("size?:");
    expect(spec.components.Tag.signature).toContain("variant?:");
  });

  it("exports json schema without legacy properties wrappers or removed host-control fields", () => {
    const schema = dslLibrary.toJSONSchema();
    const defs = ("$defs" in schema ? schema.$defs : {}) as Record<string, { properties?: Record<string, unknown> }>;

    const button = defs.Button;
    const text = defs.Text;
    const card = defs.Card;
    const cardHeader = defs.CardHeader;
    const tag = defs.Tag;
    const table = defs.Table;

    expect(button.properties).toMatchObject({
      status: expect.anything(),
      disabled: expect.anything(),
      text: expect.anything(),
      type: expect.anything(),
    });
    expect(button.properties).not.toHaveProperty("properties");
    expect(button.properties).not.toHaveProperty("style");
    expect(button.properties).not.toHaveProperty("actions");

    expect(text.properties).toMatchObject({
      size: expect.anything(),
      text: expect.anything(),
    });
    expect(text.properties).not.toHaveProperty("properties");
    expect(text.properties).not.toHaveProperty("style");

    expect(cardHeader.properties).toMatchObject({
      subtitle: expect.anything(),
      title: expect.anything(),
    });

    expect(card.properties).not.toHaveProperty("style");
    expect(card.properties).not.toHaveProperty("header");
    expect(card.properties).not.toHaveProperty("width");
    expect(card.properties).toMatchObject({
      align: expect.anything(),
      direction: expect.anything(),
      gap: expect.anything(),
      justify: expect.anything(),
      wrap: expect.anything(),
    });
    expect(JSON.stringify(card.properties)).not.toContain("\"actions\"");

    expect(tag.properties).toMatchObject({
      text: expect.anything(),
      icon: expect.anything(),
      size: expect.anything(),
      variant: expect.anything(),
    });

    expect(table.properties).not.toHaveProperty("style");
  });

  it("includes Render in static-library prompts while omitting data-only builtins", () => {
    const prompt = dslLibrary.prompt({ toolCalls: false, bindings: false });

    expect(prompt).toContain('@Render("v", expr)');
    expect(prompt).not.toContain("@Count(array)");
  });

  it("includes table-specific render and formatting guidance in the default prompt", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          employees: [{ name: "Alice", salary: 95000, joinedAt: "2023-06-15T00:00:00.000Z", active: 1 }],
        },
      },
    });

    expect(prompt).toContain('For Table column options.cell, `@Render("v", expr)` receives the cell value');
    expect(prompt).toContain('If the render body needs other fields from the row, use `@Render("v", "row", expr)`');
    expect(prompt).toContain(
      "Use `format` only for ISO date/time string fields, never for numeric fields like salary or revenue",
    );
    expect(prompt).toContain(
      'nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})',
    );
    expect(prompt).toContain(
      'statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": Text("Active"), "0": Text("Inactive")}, Text("Unknown")))})',
    );
  });
});
