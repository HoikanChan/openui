import { describe, expect, it } from "vitest";

import { type ASTNode, isASTNode } from "../parser/ast";
import { parse } from "../parser/parser";
import { isElementNode, type ParamMap } from "../parser/types";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

function createContext(refs: Record<string, unknown> = {}): EvaluationContext {
  return {
    getState: () => null,
    resolveRef: (name) => refs[name] ?? null,
  };
}

function each(array: ASTNode, binder: string, template: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Each",
    args: [array, { k: "Str", v: binder }, template],
  };
}

function render(binder: string, body: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Render",
    args: [{ k: "Str", v: binder }, body],
  };
}

function box(value: ASTNode): ASTNode {
  return {
    k: "Comp",
    name: "Box",
    args: [],
    mappedProps: { value },
  };
}

function getBoxRender(
  result: unknown,
  index = 0,
  propName = "value",
): Extract<ASTNode, { k: "Comp" }> {
  if (!Array.isArray(result) || !isElementNode(result[index])) {
    throw new Error("Expected an evaluated Box element");
  }
  const renderNode = result[index].props[propName];
  if (!isASTNode(renderNode) || renderNode.k !== "Comp" || renderNode.name !== "Render") {
    throw new Error("Expected the Box value to contain a Render AST");
  }
  return renderNode;
}

describe("@Each template lexical scope", () => {
  it("lets a nested @Each binder shadow an outer binder with the same name", () => {
    const nestedEach: ASTNode = {
      k: "Comp",
      name: "Each",
      args: [
        { k: "Arr", els: [{ k: "Str", v: "inner" }] },
        { k: "Ref", n: "item" },
        { k: "Ref", n: "item" },
      ],
    };
    const node = each({ k: "Arr", els: [{ k: "Str", v: "outer" }] }, "item", nestedEach);

    expect(evaluate(node, createContext())).toEqual([["inner"]]);
  });

  it("captures group values in extracted Table columns with deferred @Render bodies", () => {
    const catalog: ParamMap = new Map([
      ["Stack", { params: [{ name: "children", required: true }] }],
      ["Tabs", { params: [{ name: "items", required: true }] }],
      [
        "TabItem",
        {
          params: [
            { name: "value", required: true },
            { name: "label", required: true },
            { name: "content", required: true },
          ],
        },
      ],
      [
        "Table",
        {
          params: [
            { name: "columns", required: true },
            { name: "rows", required: true },
          ],
        },
      ],
      [
        "Col",
        {
          params: [
            { name: "title", required: true },
            { name: "field", required: true },
            { name: "options", required: false },
          ],
        },
      ],
      ["TextContent", { params: [{ name: "text", required: true }] }],
    ]);
    const dsl = `root = Stack([modelTabs])
modelTabs = Tabs(@Each(data.grouped_by_model, "group", modelTabTpl))
modelTabTpl = TabItem(group.model, group.model + " (" + group.count + " devices)", [modelTable])
modelTable = Table([modelCol, countCol, versionCol, nameCol, ipCol, vendorCol], group.devices)
modelCol = Col("Model", "model", {cell: @Render("v", "row", TextContent(group.model))})
countCol = Col("Count", "count", {cell: @Render("v", "row", TextContent("" + group.count))})
versionCol = Col("Version", "version")
nameCol = Col("Device Name", "name")
ipCol = Col("IP", "ip")
vendorCol = Col("Vendor", "vendor")`;
    const data = {
      grouped_by_model: [
        {
          model: "Model-X",
          count: 2,
          devices: [{ model: "row-model", count: 999, version: "1.0" }],
        },
      ],
    };
    const parsed = parse(dsl, catalog, "Stack", { externalRefs: ["data"] });

    expect(parsed.meta.errors).toEqual([]);
    expect(parsed.meta.unresolved).toEqual([]);
    if (!parsed.root) throw new Error("Expected a parsed root element");
    const children = parsed.root.props.children;
    if (!Array.isArray(children) || !isElementNode(children[0])) {
      throw new Error("Expected the root to contain Tabs");
    }
    const itemsExpression = children[0].props.items;
    if (!isASTNode(itemsExpression)) throw new Error("Expected Tabs items to contain @Each");
    const items = evaluate(itemsExpression, createContext({ data }));
    if (!Array.isArray(items) || !isElementNode(items[0])) {
      throw new Error("Expected Tabs to contain an evaluated TabItem");
    }
    const tab = items[0];
    const content = tab.props.content;
    if (!Array.isArray(content) || !isElementNode(content[0])) {
      throw new Error("Expected TabItem content to contain a Table");
    }
    const table = content[0];
    const columns = table.props.columns;
    if (!Array.isArray(columns) || !isElementNode(columns[0]) || !isElementNode(columns[1])) {
      throw new Error("Expected the Table to contain model and count columns");
    }
    const [modelColumn, countColumn] = columns;
    const getCellRender = (column: typeof modelColumn) => {
      const options = column.props.options;
      if (options === null || typeof options !== "object" || !("cell" in options)) {
        throw new Error("Expected Col options.cell");
      }
      const cell = options.cell;
      if (!isASTNode(cell) || cell.k !== "Comp" || cell.name !== "Render") {
        throw new Error("Expected Col options.cell to remain a deferred Render");
      }
      return cell;
    };
    const modelRender = getCellRender(modelColumn);
    const countRender = getCellRender(countColumn);
    const modelBody = modelRender.args.at(-1);
    const countBody = countRender.args.at(-1);

    expect(table.props.rows).toEqual(data.grouped_by_model[0].devices);
    expect(modelRender.args.slice(0, -1)).toEqual([
      { k: "Str", v: "v" },
      { k: "Str", v: "row" },
    ]);
    expect(countRender.args.slice(0, -1)).toEqual([
      { k: "Str", v: "v" },
      { k: "Str", v: "row" },
    ]);
    expect(modelBody).toMatchObject({
      k: "Comp",
      name: "TextContent",
      mappedProps: { text: { k: "Str", v: "Model-X" } },
    });
    expect(countBody).toMatchObject({
      k: "Comp",
      name: "TextContent",
      mappedProps: {
        text: {
          k: "BinOp",
          op: "+",
          left: { k: "Str", v: "" },
          right: { k: "Num", v: 2 },
        },
      },
    });
  });

  it("lets a Render binder shadow an outer @Each binding", () => {
    const templateRender: ASTNode = {
      k: "Comp",
      name: "Render",
      args: [
        { k: "Ref", n: "item" },
        { k: "Ref", n: "item" },
      ],
    };
    const node = each({ k: "Arr", els: [{ k: "Str", v: "outer" }] }, "item", box(templateRender));

    const evaluatedRender = getBoxRender(evaluate(node, createContext()));

    expect(evaluatedRender.args).toEqual([
      { k: "Ref", n: "item" },
      { k: "Ref", n: "item" },
    ]);
  });

  it("preserves a direct @Render template instead of calling it as an @Each callback", () => {
    const template = render("value", { k: "Ref", n: "value" });
    const node = each({ k: "Arr", els: [{ k: "Str", v: "outer" }] }, "item", template);

    expect(evaluate(node, createContext())).toEqual([template]);
  });

  it("creates independent captures when one template AST is reused", () => {
    const template = box(
      render("value", {
        k: "Member",
        obj: { k: "Ref", n: "row" },
        field: "id",
      }),
    );
    const node = each({ k: "Ref", n: "rows" }, "row", template);

    const firstEvaluation = evaluate(
      node,
      createContext({ rows: [{ id: "first" }, { id: "second" }] }),
    );
    const secondEvaluation = evaluate(node, createContext({ rows: [{ id: "third" }] }));
    const firstRender = getBoxRender(firstEvaluation, 0);
    const secondRender = getBoxRender(firstEvaluation, 1);
    const thirdRender = getBoxRender(secondEvaluation, 0);

    expect(firstRender.args[1]).toEqual({ k: "Str", v: "first" });
    expect(secondRender.args[1]).toEqual({ k: "Str", v: "second" });
    expect(thirdRender.args[1]).toEqual({ k: "Str", v: "third" });
    expect(firstRender).not.toBe(secondRender);
    expect(firstRender).not.toBe(thirdRender);
    expect(template).toEqual(
      box(
        render("value", {
          k: "Member",
          obj: { k: "Ref", n: "row" },
          field: "id",
        }),
      ),
    );
  });
});
