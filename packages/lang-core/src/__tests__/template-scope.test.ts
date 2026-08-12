import { describe, expect, it } from "vitest";

import { type ASTNode, isASTNode } from "../parser/ast";
import { isElementNode } from "../parser/types";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

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

function getBoxRender(result: unknown, index = 0): Extract<ASTNode, { k: "Comp" }> {
  if (!Array.isArray(result) || !isElementNode(result[index])) {
    throw new Error("Expected an evaluated Box element");
  }
  const renderNode = result[index].props.value;
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
