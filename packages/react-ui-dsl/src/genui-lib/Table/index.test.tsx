import React from "react";
import { describe, expect, it } from "vitest";
import { createParser } from "../../../../lang-core/src";
import { dslLibrary } from "../dslLibrary";
import { mapColumnsToAntd } from "./index";

function createCol(props: Record<string, unknown>) {
  return {
    type: "element" as const,
    typeName: "Col",
    partial: false,
    props,
  };
}

describe("react-ui-dsl Table schema redesign", () => {
  it("exposes openui lang-style Table and Col signatures", () => {
    const spec = dslLibrary.toSpec();

    expect(spec.components.Table.signature).toContain("Table(columns: Col[]");
    expect(spec.components.Table.signature).toContain("rows: Record<string, any>[]");
    expect(spec.components.Col.signature).toContain("Col(title: string, field: string");
    expect(spec.components.Col.signature).toContain("options?: {sortable?: boolean");
  });

  it("renders a table from declarative Col components and row data", () => {
    const parser = createParser(dslLibrary.toJSONSchema());
    const result = parser.parse(`root = Table([Col("Name", "name"), Col("Joined", "joinDate", {cell: @Render("v", Text(@FormatDate(v, "date")))})], rows)
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

    const columns = mapColumnsToAntd(
      result.root?.props.columns as any,
      (value) => <span data-rendered="true">{JSON.stringify(value)}</span>,
    );

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
