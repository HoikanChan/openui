import { describe, expect, it } from "vitest";
import { dslLibrary } from "./dslLibrary";

describe("react-ui-dsl exported prompt and schema surface", () => {
  it("exposes controlled props schemas for representative components", () => {
    const spec = dslLibrary.toSpec();
    const propsOf = (name: keyof typeof spec.components) =>
      spec.components[name].propsSchema?.properties ?? {};

    expect(Object.keys(propsOf("Button"))).toEqual([
      "label",
      "action",
      "variant",
      "type",
      "size",
      "disabled",
    ]);
    expect(propsOf("Button")).not.toHaveProperty("properties");
    expect(propsOf("Button")).not.toHaveProperty("style");
    expect(propsOf("Button")).not.toHaveProperty("actions");
    expect(propsOf("Button")).not.toHaveProperty("text");
    expect(propsOf("Button")).not.toHaveProperty("status");

    expect(Object.keys(propsOf("TextContent"))).toEqual(["text", "size"]);
    expect(propsOf("TextContent").text).toEqual({ type: "string" });
    expect(propsOf("TextContent")).not.toHaveProperty("properties");
    expect(propsOf("TextContent")).not.toHaveProperty("style");

    expect(Object.keys(propsOf("MarkDownRenderer"))).toEqual([
      "textMarkdown",
      "variant",
      "tablePageSize",
    ]);

    expect(Object.keys(propsOf("CardHeader"))).toEqual(["title", "subtitle"]);

    expect(Object.keys(propsOf("Card"))).toEqual([
      "children",
      "direction",
      "align",
      "justify",
      "wrap",
    ]);
    expect(propsOf("Card")).not.toHaveProperty("variant");
    expect(propsOf("Card")).not.toHaveProperty("width");
    expect(propsOf("Card")).not.toHaveProperty("gap");
    expect(propsOf("Card")).not.toHaveProperty("style");
    expect(propsOf("Card")).not.toHaveProperty("actions");
    expect(propsOf("Card")).not.toHaveProperty("header");

    expect(propsOf("Table")).not.toHaveProperty("style");
    expect(Object.keys(propsOf("Tag"))).toEqual(["text", "variant", "icon", "size"]);

    expect(Object.keys(propsOf("Descriptions"))).toEqual(["items", "border"]);

    expect(Object.keys(propsOf("SelectItem"))).toEqual(["label", "value"]);

    expect(Object.keys(propsOf("DescField"))).toEqual(["label", "value", "span"]);
    expect(propsOf("DescField")).not.toHaveProperty("format");
  });

  it("exports json schema without legacy properties wrappers or removed host-control fields", () => {
    const spec = dslLibrary.toSpec();
    const schema = dslLibrary.toJSONSchema();
    const defs = ("$defs" in schema ? schema.$defs : {}) as Record<
      string,
      { properties?: Record<string, unknown> }
    >;

    const button = defs.Button;
    const separator = defs.Separator;
    const textContent = defs.TextContent;
    const markDownRenderer = defs.MarkDownRenderer;
    const card = defs.Card;
    const cardHeader = defs.CardHeader;
    const descriptions = defs.Descriptions;
    const descField = defs.DescField;
    const tag = defs.Tag;
    const table = defs.Table;

    expect(button.properties).toMatchObject({
      label: expect.anything(),
      action: expect.anything(),
      variant: expect.anything(),
      type: expect.anything(),
      size: expect.anything(),
      disabled: expect.anything(),
    });
    expect(button.properties).not.toHaveProperty("text");
    expect(button.properties).not.toHaveProperty("status");
    expect(button.properties).not.toHaveProperty("properties");
    expect(button.properties).not.toHaveProperty("style");
    expect(button.properties).not.toHaveProperty("actions");

    expect(Object.keys(spec.components.Separator.propsSchema?.properties ?? {})).toEqual([
      "orientation",
      "decorative",
    ]);

    expect(separator.properties).toMatchObject({
      orientation: expect.anything(),
      decorative: expect.anything(),
    });

    expect(textContent.properties).toMatchObject({
      size: expect.anything(),
      text: expect.anything(),
    });
    expect(textContent.properties).not.toHaveProperty("properties");
    expect(textContent.properties).not.toHaveProperty("style");

    expect(markDownRenderer.properties).toMatchObject({
      textMarkdown: expect.anything(),
      variant: expect.anything(),
    });

    expect(cardHeader.properties).toMatchObject({
      subtitle: expect.anything(),
      title: expect.anything(),
    });

    expect(card.properties).not.toHaveProperty("style");
    expect(card.properties).not.toHaveProperty("header");
    expect(card.properties).not.toHaveProperty("variant");
    expect(card.properties).not.toHaveProperty("width");
    expect(card.properties).not.toHaveProperty("gap");
    expect(card.properties).toMatchObject({
      children: expect.anything(),
      direction: expect.anything(),
      align: expect.anything(),
      justify: expect.anything(),
      wrap: expect.anything(),
    });
    expect(JSON.stringify(card.properties)).not.toContain('"actions"');

    expect(tag.properties).toMatchObject({
      text: expect.anything(),
      icon: expect.anything(),
      size: expect.anything(),
      variant: expect.anything(),
    });

    expect(table.properties).not.toHaveProperty("style");

    expect(descriptions.properties).toMatchObject({
      items: expect.anything(),
      border: expect.anything(),
    });
    expect(descriptions.properties).not.toHaveProperty("title");
    expect(descriptions.properties).not.toHaveProperty("extra");
    expect(descriptions.properties).not.toHaveProperty("columns");
    expect(descField.properties).toMatchObject({
      label: expect.anything(),
      value: expect.anything(),
      span: expect.anything(),
    });
    expect(descField.properties).not.toHaveProperty("format");
  });

  it("includes Render in static-library prompts while omitting data-only builtins", () => {
    const prompt = dslLibrary.prompt({ toolCalls: false, bindings: false });

    expect(prompt).toContain('@Render("v", expr)');
    expect(prompt).not.toContain("@Count(array)");
  });

  it("includes table-specific render and @Format guidance in the default prompt", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          employees: [
            { name: "Alice", salary: 95000, joinedAt: "2023-06-15T00:00:00.000Z", active: 1 },
          ],
        },
      },
    });

    expect(prompt).toContain(
      'For Table column options.cell, `@Render("v", expr)` receives the cell value',
    );
    expect(prompt).toContain(
      'If the render body needs other fields from the row, use `@Render("v", "row", expr)`',
    );
    expect(prompt).toContain(
      "If a table cell label must combine the current value with another field from the same row",
    );
    expect(prompt).toContain(
      "Use `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration`",
    );
    expect(prompt).toContain("Never use the removed `format` prop on `Col` or `DescField`");
    expect(prompt).toContain("For dynamic-key object maps");
    expect(prompt).toContain(
      'nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})',
    );
    expect(prompt).toContain(
      'joinedCol = Col("Joined", "joinedAt", {cell: @Render("v", TextContent(@FormatDate(v, "date")))})',
    );
    expect(prompt).toContain(
      'statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": TextContent("Active"), "0": TextContent("Inactive")}, TextContent("Unknown")))})',
    );
    expect(prompt).toContain(
      'statusCol = Col("Status", "status", {cell: @Render("v", "row", TextContent(row.id + ": " + @Switch(v, {"paid": "Paid", "pending": "Pending"}, "Unknown")))})',
    );
    expect(prompt).toContain("deviceRows = @ObjectEntries(data.devicesById)");
    expect(prompt).toContain("deviceTable = Table([deviceKeyCol, statusCol], deviceRows)");
    expect(prompt).toContain('deviceKeyCol = Col("Device", "key")');
    expect(prompt).toContain('statusCol = Col("Status", "value.status")');
  });

  it("guides generation away from JavaScript conversion helpers", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          total: 347,
          pageSize: 20,
          pageIndex: 1,
        },
      },
    });

    expect(prompt).toContain("Never use JavaScript conversion constructors");
    expect(prompt).toContain("String(...), Number(...), Boolean(...), Date(...), or Math.*");
    expect(prompt).toContain("openui-lang concatenation");
    expect(prompt).toContain("@Ceil");
  });

  it("includes paginated envelope guidance without static pagination controls", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          total: 347,
          pageSize: 20,
          pageIndex: 1,
          list: [{ id: "row-1", status: "open" }],
        },
      },
    });

    expect(prompt).toContain("For paginated envelopes");
    expect(prompt).toContain("total/pageSize/pageIndex");
    expect(prompt).toContain("metadata with Descriptions");
    expect(prompt).toContain("Table");
    expect(prompt).toContain(
      'pageField = DescField("Page", data.pageIndex + " / " + @Ceil(data.total / data.pageSize))',
    );
    expect(prompt).toContain("Do not add previous/next Button controls");
  });

  it("includes shape-aware formatting guidance for API-shaped tuple, byte, and ratio data", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          samples: [
            [1717190400000, 12.4],
            [1717191300000, 15.7],
          ],
          volumes: [
            { name: "OS Disk", totalBytes: 107374182400, usedBytes: 45678901234 },
            { name: "Archive", totalBytes: 1125899906842624, usedBytes: 456789012345678 },
          ],
        },
      },
    });

    expect(prompt).toContain("For positional tuple arrays");
    expect(prompt).toContain("project the needed tuple index before formatting");
    expect(prompt).toContain(
      'timestampLabels = @Each(data.samples, "item", @FormatDate(item[0], "dateTime"))',
    );
    expect(prompt).toContain("Byte-count fields");
    expect(prompt).toContain("`*Bytes`");
    expect(prompt).toContain("@FormatBytes");
    expect(prompt).toContain(
      "Rate fields such as `bandwidth`, `bps`, `bitrate`, or `bitsPerSecond`",
    );
    expect(prompt).toContain(
      'TextContent(v >= 1000000000 ? @FormatNumber(v / 1000000000, 1) + " Gbps"',
    );
    expect(prompt).toContain(
      "Do not compute utilization percentages by dividing cumulative byte totals by bandwidth or bitrate fields",
    );
    expect(prompt).toContain("Ratios derived from fields such as `used / total`");
    expect(prompt).toContain(
      '@Render("v", "row", TextContent(@FormatPercent(row.usedBytes / row.totalBytes, 1)))',
    );
    expect(prompt).toContain("Do not declare pseudo-reusable component templates");
    expect(prompt).toContain("prefer one clear Table with formatted columns");
    expect(prompt).toContain("Do not add a chart that repeats the same homogeneous records");
    expect(prompt).not.toContain("timeseries-tuple-pairs");
    expect(prompt).not.toContain("cross-magnitude-values");
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

    expect(prompt).toContain(
      "Only use chart components when the data model already exposes chart-ready fields",
    );
    expect(prompt).toContain(
      "Do not invent labels, series, categories, or missing time points from raw rows",
    );
    expect(prompt).toContain(
      "If the data model only contains raw row records, prefer Table or Descriptions",
    );
    expect(prompt).toContain(
      "rawRowsTable = Table([deviceCol, interfaceCol, timeCol, utilizationCol], data.rows)",
    );
  });

  it("includes neutral null-dominant anti-fabrication guidance", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          record: {
            id: "r-001",
            name: "Record One",
            file: null,
            status: null,
            metric: null,
            note: null,
          },
        },
      },
    });

    expect(prompt).toContain("null-dominant");
    expect(prompt).toContain('data.record.file ?? "No data"');
    expect(prompt).toContain('DescField("ID", data.record.id ?? "No data")');
    expect(prompt).toContain(
      "Never synthesize rows, metrics, statuses, timestamps, percentages, details",
    );
    expect(prompt).not.toContain("For unlabeled ratio arrays");
    expect(prompt).not.toContain("data.deviceId");
    expect(prompt).not.toContain("CPU Utilization");
  });
});
