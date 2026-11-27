import { describe, expect, it } from "vitest";
import { createParser } from "../../../../lang-core/src";
import { dslLibrary } from "../dslLibrary";
import { mapColumnsToAntd } from "./view/antd.js";
import { TableView } from "./view/antd.js";

function createCol(props: Record<string, unknown>) {
  return {
    type: "element" as const,
    typeName: "Col",
    partial: false,
    props,
  };
}

describe("react-ui-dsl Table schema redesign", () => {
  it("exposes openui lang-style Table and Col props schemas", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.Table.propsSchema?.properties.columns).toEqual({
      type: "array",
      items: {
        anyOf: [{ component: "Col" }, { component: "ExtraCol" }, { component: "IndexCol" }],
      },
    });
    expect(spec.components.Table.propsSchema?.properties.rows).toEqual({
      type: "array",
      items: {
        type: "object",
        additionalProperties: { type: "any" },
      },
    });
    expect(Object.keys(spec.components.Col.propsSchema?.properties ?? {})).toEqual([
      "title",
      "field",
      "options",
    ]);
    expect(spec.components.Col.propsSchema?.properties.options).toMatchObject({
      type: "object",
      properties: {
        sortable: { type: "boolean" },
      },
    });
  });

  it("renders a table from declarative Col components and row data", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result =
      parser.parse(`root = Table([Col("Name", "name"), Col("Joined", "joinDate", {cell: @Render("v", TextContent(@FormatDate(v, "date")))})], rows)
rows = [{name: "Alice", joinDate: "2026-01-02T03:04:05.000Z"}]`);

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.typeName).toBe("Table");
    expect(result.root?.props.columns).toHaveLength(2);
    expect(result.root?.props.columns[0]).toMatchObject({
      type: "element",
      typeName: "Col",
      props: { title: "Name", field: "name" },
    });
    expect(result.root?.props.rows[0]).toEqual({
      name: "Alice",
      joinDate: "2026-01-02T03:04:05.000Z",
    });
  });

  it("allows ObjectEntries rows to feed iterable table patterns", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(
      `root = Table([Col("Device", "key"), Col("Status", "value.status")], @ObjectEntries(data.devicesById))`,
    );

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.typeName).toBe("Table");
    expect(result.root?.props.columns).toHaveLength(2);
    expect(result.root?.props.columns[0]).toMatchObject({
      type: "element",
      typeName: "Col",
      props: { title: "Device", field: "key" },
    });
    expect(result.root?.props.columns[1]).toMatchObject({
      type: "element",
      typeName: "Col",
      props: { title: "Status", field: "value.status" },
    });
    expect(result.root?.props.rows).toMatchObject({
      k: "Comp",
      name: "ObjectEntries",
    });
  });

  it("maps column options to antd table config", () => {
    const columns = mapColumnsToAntd(
      [
        createCol({
          title: "Name",
          field: "name",
          options: {
            sortable: true,
            filterable: true,
            filterOptions: ["Alice"],
            tooltip: true,
          },
        }).props,
        createCol({
          title: "Status",
          field: "status",
          options: {
            cell: { type: "element", typeName: "Text", partial: false, props: { value: "Custom" } },
          },
        }).props,
      ],
      (value) => <span data-rendered="true">{JSON.stringify(value)}</span>,
    );

    expect(columns).toHaveLength(2);
    expect(columns[0].title).toBe("Name");
    expect(columns[0].dataIndex).toBe("name");
    expect(typeof columns[0].sorter).toBe("function");
    expect(columns[0].filters).toEqual([{ text: "Alice", value: "Alice" }]);

    const tooltipCell = columns[0].render?.("Alice");
    expect(tooltipCell.props.title).toBe("Alice");

    const customCell = columns[1].render?.("ignored");
    expect(customCell.props["data-rendered"]).toBe("true");
  });

  it("invokes function cell renderers with value and row record", () => {
    const columns = mapColumnsToAntd([
      createCol({
        title: "Status",
        field: "status",
        options: {
          cell: (value: unknown, record: { id: number; status: string }) =>
            `${record.id}:${String(value)}:${record.status}`,
        },
      }).props,
    ]);

    const rendered = columns[0].render?.("Open", { id: 7, status: "Open" } as any);
    expect(rendered).toBe("7:Open:Open");
  });

  it("maps parsed Col element nodes to antd table columns", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Table([Col("Name", "name"), Col("Status", "status")], rows)
rows = [{name: "Alice", status: "Active"}]`);

    const columns = mapColumnsToAntd(result.root?.props.columns as any, (value) => (
      <span data-rendered="true">{JSON.stringify(value)}</span>
    ));

    expect(columns).toHaveLength(2);
    expect(columns[0].title).toBe("Name");
    expect(columns[0].dataIndex).toBe("name");
    expect(columns[1].title).toBe("Status");
    expect(columns[1].dataIndex).toBe("status");
  });

  it("rejects the removed format column option with a migration-oriented error", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Table([Col("Joined", "joinDate", {format: "date"})], rows)
rows = [{joinDate: "2026-01-02T03:04:05.000Z"}]`);

    expect(result.meta.errors.length).toBeGreaterThan(0);
    expect(JSON.stringify(result.meta.errors)).toContain("format");
  });

  it("rejects the legacy JSON-style table signature", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Table({columns: [{title: "Name", field: "name"}]})`);

    expect(result.meta.errors.length).toBeGreaterThan(0);
  });
});

describe("Table expandRow DSL parsing", () => {
  it("parses @Render as the 3rd positional argument", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(
      `root = Table([Col("Device", "name"), Col("Status", "status")], devices, @Render("device", Table([Col("Interface", "name")], device.interfaces)))
devices = [{name: "Router-A", status: "up", interfaces: [{name: "eth0"}]}]`,
    );

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.props.expandRow).toBeDefined();
  });
});

function createColumnNode(typeName: string, props: Record<string, unknown>) {
  return { type: "element" as const, typeName, partial: false, props };
}

describe("Table ExtraCol and IndexCol columns", () => {
  it("exposes ExtraCol and IndexCol schemas alongside Col", () => {
    const defs = dslLibrary.toJSONSchema().$defs ?? {};
    expect(defs.ExtraCol?.properties?.title).toEqual({ type: "string" });
    expect(defs.ExtraCol?.required).toEqual(["title", "value"]);
    expect(defs.IndexCol?.properties?.title).toEqual({ type: "string" });
    expect(defs.IndexCol?.required).toEqual(["title"]);
  });

  it("parses IndexCol and ExtraCol columns in a table", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(
      `root = Table([IndexCol("No."), Col("Name", "name"), ExtraCol("Site", "Beijing")], rows)
rows = [{name: "Alice"}]`,
    );

    expect(result.meta.errors).toHaveLength(0);
    expect(result.root?.props.columns).toHaveLength(3);
    expect(result.root?.props.columns[0]).toMatchObject({
      type: "element",
      typeName: "IndexCol",
      props: { title: "No." },
    });
    expect(result.root?.props.columns[2]).toMatchObject({
      type: "element",
      typeName: "ExtraCol",
      props: { title: "Site", value: "Beijing" },
    });
  });

  it("renders one-based row index cells from IndexCol", () => {
    const columns = mapColumnsToAntd([createColumnNode("IndexCol", { title: "No." })]);
    expect(columns[0].title).toBe("No.");
    expect(columns[0].render?.("ignored", {}, 0)).toBe("1");
    expect(columns[0].render?.("ignored", {}, 4)).toBe("5");
  });

  it("renders the fixed ExtraCol value for every row", () => {
    const columns = mapColumnsToAntd([
      createColumnNode("ExtraCol", { title: "Site", value: "Beijing" }),
    ]);
    expect(columns[0].title).toBe("Site");
    expect(columns[0].render?.("ignored", {})).toBe("Beijing");
    expect(columns[0].render?.("ignored", {})).toBe("Beijing");
  });

  it("calls a hydrated @Render slot with the current row", () => {
    const columns = mapColumnsToAntd([
      createColumnNode("ExtraCol", {
        title: "Action",
        value: (record: { id: number }) => `row-${record.id}`,
      }),
    ]);
    expect(columns[0].render?.("ignored", { id: 7 })).toBe("row-7");
  });

  it("renders an ExtraCol element value through renderNode", () => {
    const element = {
      type: "element" as const,
      typeName: "TextContent",
      partial: false,
      props: { text: "Row action" },
    };
    const columns = mapColumnsToAntd(
      [createColumnNode("ExtraCol", { title: "Action", value: element })],
      (value) => <span data-rendered="true">{JSON.stringify(value)}</span>,
    );
    const rendered = columns[0].render?.("ignored", {});
    expect(rendered.props["data-rendered"]).toBe("true");
  });
});

describe("Table expandRow", () => {
  const rows = [{ id: 1 }, { id: 2 }];

  it("renders expand chevron and content when expandRow is provided", () => {
    const expandFn = (record: unknown) => <span>{JSON.stringify(record)}</span>;
    const view = TableView({ columns: [], rows, expandRow: expandFn });

    expect(view.props.expandable).toBeDefined();
    expect(typeof view.props.expandable.expandedRowRender).toBe("function");
    const content = view.props.expandable.expandedRowRender({ id: 1 });
    expect(content.props.children).toBe(JSON.stringify({ id: 1 }));
  });

  it("sets defaultExpandAllRows true when rows.length <= 3", () => {
    const expandFn = () => <span>details</span>;
    const view = TableView({
      columns: [],
      rows: [{ id: 1 }, { id: 2 }, { id: 3 }],
      expandRow: expandFn,
    });

    expect(view.props.expandable.defaultExpandAllRows).toBe(true);
  });

  it("does not add expandable config when expandRow is omitted", () => {
    const view = TableView({ columns: [], rows });

    expect(view.props.expandable).toBeUndefined();
  });
});
