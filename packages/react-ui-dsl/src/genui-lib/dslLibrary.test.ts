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

    expect(spec.components.Descriptions.signature).toContain("Descriptions(items:");
    expect(spec.components.Descriptions.signature).toContain("title?:");
    expect(spec.components.Descriptions.signature).toContain("extra?:");
    expect(spec.components.Descriptions.signature).toContain("columns?:");
    expect(spec.components.Descriptions.signature).toContain("border?:");

    expect(spec.components.DescGroup.signature).toContain("DescGroup(title: string");
    expect(spec.components.DescGroup.signature).toContain("fields:");
    expect(spec.components.DescGroup.signature).toContain("columns?:");

    expect(spec.components.DescField.signature).toContain("DescField(label: string");
    expect(spec.components.DescField.signature).toContain("value:");
    expect(spec.components.DescField.signature).toContain("span?:");
    expect(spec.components.DescField.signature).toContain("format?:");
  });

  it("exports json schema without legacy properties wrappers or removed host-control fields", () => {
    const schema = dslLibrary.toJSONSchema();
    const defs = ("$defs" in schema ? schema.$defs : {}) as Record<string, { properties?: Record<string, unknown> }>;

    const button = defs.Button;
    const text = defs.Text;
    const card = defs.Card;
    const cardHeader = defs.CardHeader;
    const descriptions = defs.Descriptions;
    const descGroup = defs.DescGroup;
    const descField = defs.DescField;
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

    expect(descriptions.properties).toMatchObject({
      items: expect.anything(),
      title: expect.anything(),
      extra: expect.anything(),
      columns: expect.anything(),
      border: expect.anything(),
    });
    expect(descGroup.properties).toMatchObject({
      title: expect.anything(),
      fields: expect.anything(),
      columns: expect.anything(),
    });
    expect(descField.properties).toMatchObject({
      label: expect.anything(),
      value: expect.anything(),
      span: expect.anything(),
      format: expect.anything(),
    });
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

  it("includes descriptions-specific guidance in the default prompt", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          user: { name: "Alice", email: "alice@example.com", status: "active" },
        },
      },
    });

    expect(prompt).toContain("Use Descriptions for single-record detail views instead of Table");
    expect(prompt).toContain('detail = Descriptions([DescField("Name", data.user.name)');
    expect(prompt).toContain('DescField("Status", Tag(data.user.status, "success"))');
  });

  it("includes chart guidance that forbids inventing derived series from raw rows", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          rows: [
            {
              deviceName: "NE-01-Core-Switch",
              showName: "GigabitEthernet0/0/1",
              time: 1717200000000,
              PeakBandwidthUtilization: 45.7,
              portResId: "550e8400-e29b-41d4-a716-446655440001",
            },
            {
              deviceName: "NE-02-Access-Router",
              showName: "Ethernet1/1",
              time: 1717200000000,
              PeakBandwidthUtilization: 18.2,
              portResId: "550e8400-e29b-41d4-a716-446655440002",
            },
          ],
          times: {
            period: 60,
            startTime: 1716595200000,
            endTime: 1717200000000,
          },
        },
      },
    });

    expect(prompt).toContain("Only use chart components when the data model already exposes chart-ready fields");
    expect(prompt).toContain("Do not invent labels, series, categories, or missing time points from raw rows");
    expect(prompt).toContain("If the data model only contains raw row records, prefer Table or Descriptions");
    expect(prompt).toContain('rawRowsTable = Table([deviceCol, interfaceCol, timeCol, utilizationCol], data.rows)');
  });
});
