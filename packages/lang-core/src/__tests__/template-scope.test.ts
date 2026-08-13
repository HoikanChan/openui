import { describe, expect, it } from "vitest";

import type { Library } from "../library";
import { type ASTNode, isASTNode } from "../parser/ast";
import { parse } from "../parser/parser";
import { isElementNode, type ParamMap } from "../parser/types";
import { evaluateElementProps } from "../runtime/evaluate-tree";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";
import { instantiateTemplate } from "../runtime/template-scope";

function createContext(
  refs: Record<string, unknown> = {},
  getState: EvaluationContext["getState"] = () => null,
): EvaluationContext {
  return {
    getState,
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

function invokeRender(
  renderNode: Extract<ASTNode, { k: "Comp" }>,
  values: readonly unknown[],
  context: EvaluationContext,
): unknown {
  const binderNodes = renderNode.args.slice(0, -1);
  const body = renderNode.args.at(-1);
  if (body === undefined || binderNodes.length !== values.length) {
    throw new Error("Render invocation does not match its binders");
  }
  const bindings = new Map<string, unknown>();
  binderNodes.forEach((binder, index) => {
    const name = binder.k === "Str" ? binder.v : binder.k === "Ref" ? binder.n : null;
    if (name === null) throw new Error("Expected a Render binder name");
    bindings.set(name, values[index]);
  });

  return evaluate(body, {
    ...context,
    resolveRef: (name) => (bindings.has(name) ? bindings.get(name) : context.resolveRef(name)),
  });
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

  it("captures an extracted template graph and evaluates deferred Render bindings live", () => {
    const catalog: ParamMap = new Map([
      ["Root", { params: [{ name: "children", required: true }] }],
      [
        "Box",
        {
          params: [
            { name: "outerId", required: true },
            { name: "render", required: true },
          ],
        },
      ],
      [
        "Cell",
        {
          params: [
            { name: "outerId", required: true },
            { name: "value", required: true },
            { name: "selected", required: true },
          ],
        },
      ],
    ]);
    const parsed = parse(
      `root = Root(@Each(rows, "row", rowTemplate))
rows = [{id: "captured"}]
rowTemplate = Box(row.id, deferredCell)
deferredCell = @Render("value", Cell(row.id, value, $selected + ""))
$selected = "declared"`,
      catalog,
    );
    const eachNode = parsed.root?.props.children;
    if (!isASTNode(eachNode)) throw new Error("Expected a materialized Each expression");

    let selected = "before";
    const context = createContext({ value: "global" }, () => selected);
    const evaluatedRows = evaluate(eachNode, context);
    if (!Array.isArray(evaluatedRows) || !isElementNode(evaluatedRows[0])) {
      throw new Error("Expected the Each expression to evaluate a Box element");
    }
    const boxNode = evaluatedRows[0];
    const renderNode = getBoxRender(evaluatedRows, 0, "render");
    const body = renderNode.args.at(-1);
    if (!isASTNode(body) || body.k !== "Comp" || body.name !== "Cell") {
      throw new Error("Expected a materialized Cell Render body");
    }

    expect(parsed.meta.errors).toEqual([]);
    expect(parsed.meta.unresolved).toEqual([]);
    expect(parsed.meta.orphaned).toEqual([]);
    expect(boxNode.props.outerId).toBe("captured");
    expect(body.args).toEqual([
      { k: "Str", v: "captured" },
      { k: "Ref", n: "value" },
      {
        k: "BinOp",
        op: "+",
        left: { k: "StateRef", n: "$selected" },
        right: { k: "Str", v: "" },
      },
    ]);
    expect(body.mappedProps).toEqual({
      outerId: body.args[0],
      value: body.args[1],
      selected: body.args[2],
    });
    expect(Object.values(body.mappedProps ?? {})).toEqual(body.args);

    const before = invokeRender(renderNode, ["call-time"], context);
    selected = "after";
    const after = invokeRender(renderNode, ["call-time"], context);

    expect(before).toMatchObject({
      type: "element",
      typeName: "Cell",
      props: { outerId: "captured", value: "call-time", selected: "before" },
    });
    expect(after).toMatchObject({
      type: "element",
      typeName: "Cell",
      props: { outerId: "captured", value: "call-time", selected: "after" },
    });
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
    const library = {
      components: Object.fromEntries(
        [...catalog.keys()].map((name) => [name, { props: { shape: {} } }]),
      ),
    } as unknown as Library;

    expect(parsed.meta.errors).toEqual([]);
    expect(parsed.meta.unresolved).toEqual([]);
    if (!parsed.root) throw new Error("Expected a parsed root element");

    const evaluated = evaluateElementProps(parsed.root, {
      ctx: createContext({ data }),
      library,
      store: null,
    });
    const children = evaluated.props.children;
    if (!Array.isArray(children) || !isElementNode(children[0])) {
      throw new Error("Expected the root to contain Tabs");
    }
    const tabs = children[0];
    const items = tabs.props.items;
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

  it("captures an outer @Each binding in a nested deferred Render body", () => {
    const node = each(
      {
        k: "Arr",
        els: [{ k: "Obj", entries: [["label", { k: "Str", v: "captured" }]] }],
      },
      "row",
      box(
        render("cell", {
          k: "Member",
          obj: { k: "Ref", n: "row" },
          field: "label",
        }),
      ),
    );

    const renderNode = getBoxRender(evaluate(node, createContext()));

    expect(renderNode.args).toEqual([
      { k: "Str", v: "cell" },
      { k: "Str", v: "captured" },
    ]);
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

  it("keeps StateRef nodes live inside captured templates", () => {
    let stateValue = "before";
    const context = createContext({}, () => stateValue);
    const node = each(
      { k: "Arr", els: [{ k: "Num", v: 1 }] },
      "item",
      box(render("value", { k: "StateRef", n: "selected" })),
    );

    const renderNode = getBoxRender(evaluate(node, context));
    const body = renderNode.args[1];
    stateValue = "after";

    expect(body).toEqual({ k: "StateRef", n: "selected" });
    expect(evaluate(body, context)).toBe("after");
  });

  it("does not fold an uncaptured object member in a deferred body", () => {
    const member: ASTNode = {
      k: "Member",
      obj: { k: "Obj", entries: [["label", { k: "Str", v: "literal" }]] },
      field: "label",
    };
    const node = each(
      { k: "Arr", els: [{ k: "Num", v: 1 }] },
      "item",
      box(render("value", member)),
    );

    const renderNode = getBoxRender(evaluate(node, createContext()));

    expect(renderNode.args[1]).toEqual(member);
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

describe("malformed lazy template scope", () => {
  it("preserves a malformed Each without capturing its arguments or mapped props", () => {
    const node: ASTNode = {
      k: "Comp",
      name: "Each",
      args: [
        { k: "Ref", n: "item" },
        { k: "Str", v: "item" },
      ],
      mappedProps: { value: { k: "Ref", n: "item" } },
    };

    expect(instantiateTemplate(node, new Map([["item", "captured"]]))).toBe(node);
  });

  it("preserves a malformed Render without capturing its arguments or mapped props", () => {
    const node: ASTNode = {
      k: "Comp",
      name: "Render",
      args: [{ k: "Ref", n: "item" }],
      mappedProps: { value: { k: "Ref", n: "item" } },
    };

    expect(instantiateTemplate(node, new Map([["item", "captured"]]))).toBe(node);
  });
});
