import { describe, expect, it } from "vitest";
import { dslLibrary } from "./dslLibrary";

describe("react-ui-dsl exported prompt and schema surface", () => {
  it("exposes flattened component signatures for representative components", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.Button.signature).toContain("Button(text?:");
    expect(spec.components.Button.signature).not.toContain("properties");
    expect(spec.components.Button.signature).not.toContain("style");
    expect(spec.components.Button.signature).not.toContain("actions");

    expect(spec.components.Text.signature).toContain("Text(content: string");
    expect(spec.components.Text.signature).toContain("type?:");
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
  });

  it("exports json schema without legacy properties wrappers or removed host-control fields", () => {
    const schema = dslLibrary.toJSONSchema();
    const defs = ("$defs" in schema ? schema.$defs : {}) as Record<string, { properties?: Record<string, unknown> }>;

    const button = defs.Button;
    const text = defs.Text;
    const card = defs.Card;
    const cardHeader = defs.CardHeader;
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
      content: expect.anything(),
      type: expect.anything(),
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

    expect(table.properties).not.toHaveProperty("style");
  });
});
