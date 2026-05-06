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

    expect(spec.components.Card.signature).toContain("variant?:");
    expect(spec.components.Card.signature).toContain("width?:");
    expect(spec.components.Card.signature).not.toContain("direction");
    expect(spec.components.Card.signature).not.toContain("gap?:");
    expect(spec.components.Card.signature).not.toContain("style");
    expect(spec.components.Card.signature).not.toContain("actions");
    expect(spec.components.Card.signature).not.toContain("header");

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
    expect(spec.components.DescField.signature).not.toContain("format?:");

    expect(spec.components.MiniChart.signature).toContain("MiniChart(type:");
    expect(spec.components.MiniChart.signature).toContain("data:");
    expect(spec.components.MiniChart.signature).toContain("height?:");
    expect(spec.components.MiniChart.signature).toContain("color?:");
    expect(spec.components.MiniChart.signature).not.toContain("size?:");
    expect(spec.components.MiniChart.signature).not.toContain("labels:");
    expect(spec.components.MiniChart.signature).not.toContain("series:");
  });

  it("exports json schema without legacy properties wrappers or removed host-control fields", () => {
    const spec = dslLibrary.toSpec();
    const schema = dslLibrary.toJSONSchema();
    const defs = ("$defs" in schema ? schema.$defs : {}) as Record<string, { properties?: Record<string, unknown> }>;

    const button = defs.Button;
    const separator = defs.Separator;
    const text = defs.Text;
    const card = defs.Card;
    const cardHeader = defs.CardHeader;
    const descriptions = defs.Descriptions;
    const descGroup = defs.DescGroup;
    const descField = defs.DescField;
    const tag = defs.Tag;
    const table = defs.Table;
    const miniChart = defs.MiniChart;

    expect(button.properties).toMatchObject({
      status: expect.anything(),
      disabled: expect.anything(),
      text: expect.anything(),
      type: expect.anything(),
    });
    expect(button.properties).not.toHaveProperty("properties");
    expect(button.properties).not.toHaveProperty("style");
    expect(button.properties).not.toHaveProperty("actions");

    expect(spec.components.Separator.signature).toContain("Separator(");
    expect(spec.components.Separator.signature).toContain("orientation?:");
    expect(spec.components.Separator.signature).toContain("decorative?:");

    expect(separator.properties).toMatchObject({
      orientation: expect.anything(),
      decorative: expect.anything(),
    });

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
    expect(card.properties).not.toHaveProperty("direction");
    expect(card.properties).not.toHaveProperty("gap");
    expect(card.properties).not.toHaveProperty("align");
    expect(card.properties).not.toHaveProperty("justify");
    expect(card.properties).not.toHaveProperty("wrap");
    expect(card.properties).toMatchObject({
      variant: expect.anything(),
      width: expect.anything(),
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
    });
    expect(descField.properties).not.toHaveProperty("format");

    expect(miniChart.properties).toMatchObject({
      type: expect.anything(),
      data: expect.anything(),
      height: expect.anything(),
      color: expect.anything(),
    });
    expect(miniChart.properties).not.toHaveProperty("size");
    expect(miniChart.properties).not.toHaveProperty("labels");
    expect(miniChart.properties).not.toHaveProperty("series");
    expect(miniChart.properties).not.toHaveProperty("xLabel");
    expect(miniChart.properties).not.toHaveProperty("yLabel");
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
          employees: [{ name: "Alice", salary: 95000, joinedAt: "2023-06-15T00:00:00.000Z", active: 1 }],
        },
      },
    });

    expect(prompt).toContain('For Table column options.cell, `@Render("v", expr)` receives the cell value');
    expect(prompt).toContain('If the render body needs other fields from the row, use `@Render("v", "row", expr)`');
    expect(prompt).toContain("If a table cell label must combine the current value with another field from the same row");
    expect(prompt).toContain("Use `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration`");
    expect(prompt).toContain("Never use the removed `format` prop on `Col` or `DescField`");
    expect(prompt).toContain("For dynamic-key object maps");
    expect(prompt).toContain(
      'nameCol = Col("Name", "name", {cell: @Render("v", "row", Link("http://localhost:5173/" + row.name, v))})',
    );
    expect(prompt).toContain(
      'joinedCol = Col("Joined", "joinedAt", {cell: @Render("v", Text(@FormatDate(v, "date")))})',
    );
    expect(prompt).toContain(
      'statusCol = Col("Status", "active", {cell: @Render("v", @Switch(v, {"1": Text("Active"), "0": Text("Inactive")}, Text("Unknown")))})',
    );
    expect(prompt).toContain(
      'statusCol = Col("Status", "status", {cell: @Render("v", "row", Text(row.id + ": " + @Switch(v, {"paid": "Paid", "pending": "Pending"}, "Unknown")))})',
    );
    expect(prompt).toContain('deviceRows = @ObjectEntries(data.devicesById)');
    expect(prompt).toContain('deviceTable = Table([deviceKeyCol, statusCol], deviceRows)');
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
    expect(prompt).toContain('timestampLabels = @Each(data.samples, "item", @FormatDate(item[0], "dateTime"))');
    expect(prompt).toContain("Byte-count fields");
    expect(prompt).toContain("`*Bytes`");
    expect(prompt).toContain("@FormatBytes");
    expect(prompt).toContain("Rate fields such as `bandwidth`, `bps`, `bitrate`, or `bitsPerSecond`");
    expect(prompt).toContain('Text(v >= 1000000000 ? @FormatNumber(v / 1000000000, 1) + " Gbps"');
    expect(prompt).toContain("Do not compute utilization percentages by dividing cumulative byte totals by bandwidth or bitrate fields");
    expect(prompt).toContain("Ratios derived from fields such as `used / total`");
    expect(prompt).toContain('@Render("v", "row", Text(@FormatPercent(row.usedBytes / row.totalBytes, 1)))');
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
    expect(prompt).toContain('detail = Descriptions([DescField("Name", data.user.name)');
    expect(prompt).toContain('DescField("Status", Tag(data.user.status, "success"))');
    expect(prompt).toContain('DescField("Joined", @FormatDate(data.user.joinedAt, "dateTime"), 2)');
  });

  it("includes timeline guidance for direct host-data bindings", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          timeline: {
            title: "Deployment History",
            items: [
              {
                title: "v2.1.0 deployed to production",
                description: "Production deployment completed successfully.",
                status: "success",
              },
            ],
          },
        },
      },
    });

    expect(prompt).toContain(
      "If timeline rows already expose `title`, `description`, and `status`, pass them directly to `TimeLine(data.timeline.items, data.timeline.title)`",
    );
    expect(prompt).toContain("timelineComponent = TimeLine(data.timeline.items, data.timeline.title)");
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
    expect(prompt).toContain("Never synthesize rows, metrics, statuses, timestamps, percentages, details");
    expect(prompt).not.toContain("For unlabeled ratio arrays");
    expect(prompt).not.toContain("data.deviceId");
    expect(prompt).not.toContain("CPU Utilization");
  });

  it("includes MiniChart guidance as a compact single-series trend primitive", () => {
    const prompt = dslLibrary.prompt({
      dataModel: {
        raw: {
          metrics: {
            sparkline: [12, 18, 15, 21],
          },
        },
      },
    });

    expect(prompt).toContain("MiniChart");
    expect(prompt).toContain("compact single-series trend primitive");
    expect(prompt).toContain("Treat primitive numeric arrays (`number[]`) as compact quantitative series");
    expect(prompt).toContain("When a `number[]` appears as a field inside records shown in a Table");
    expect(prompt).toContain('Col(..., {cell: @Render("v", MiniChart(..., v))})');
    expect(prompt).toContain("MiniChart accepts `number[]` directly and derives compact point labels automatically");
    expect(prompt).toContain("For standalone numeric arrays rendered with MiniChart, keep the chart compact");
    expect(prompt).toContain("Include scalar identifier and context fields such as id, name, title, endpoint, label, unit, count, current, min, max, avg, p95");
    expect(prompt).toContain("Never set MiniChart height above 96 for a primitive numeric array");
    expect(prompt).toContain("MiniChart(\"line\", data.metrics.sparkline");
    expect(prompt).toContain('valuesCol = Col("Values", "values", {cell: @Render("v", MiniChart("line", v))})');
    expect(prompt).toContain('measurementsChart = MiniChart("bar", data.summary.measurements');
    expect(prompt).toContain("Omit MiniChart height unless the layout needs a tighter or taller trend");
    expect(prompt).not.toContain("MiniChart(data.labels");
  });
});
