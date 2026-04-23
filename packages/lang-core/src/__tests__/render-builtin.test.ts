import { describe, expect, it } from "vitest";

import { type ASTNode } from "../parser/ast";
import { LAZY_BUILTIN_DEFS, LAZY_BUILTINS, isBuiltin } from "../parser/builtins";
import { type MaterializeCtx, materializeValue } from "../parser/materialize";
import { generatePrompt, type PromptSpec } from "../parser/prompt";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

function createMaterializeCtx(): MaterializeCtx {
  return {
    syms: new Map(),
    cat: undefined,
    errors: [],
    unres: [],
    visited: new Set(),
    partial: false,
  };
}

const basePromptSpec: PromptSpec = {
  root: "Root",
  components: {
    Root: { signature: "Root(children: Component[])", description: "Root container" },
    Table: { signature: "Table(columns: Component[])", description: "Table container" },
  },
};

const evalContext: EvaluationContext = {
  getState: () => null,
  resolveRef: () => null,
};

describe("Render builtin", () => {
  it("registers Render as a lazy builtin", () => {
    expect(isBuiltin("Render")).toBe(true);
    expect(LAZY_BUILTINS.has("Render")).toBe(true);
    expect(LAZY_BUILTIN_DEFS.Render).toMatchObject({
      signature: expect.stringContaining('Render("v", expr)'),
      description: expect.any(String),
    });
  });

  it("materializes the single-variable form without unresolved refs", () => {
    const ctx = createMaterializeCtx();
    const node: ASTNode = {
      k: "Comp",
      name: "Render",
      args: [
        { k: "Str", v: "v" },
        {
          k: "Comp",
          name: "Switch",
          args: [
            { k: "Ref", n: "v" },
            {
              k: "Obj",
              entries: [["ok", { k: "Str", v: "OK" }]],
            },
          ],
        },
      ],
    };

    const result = materializeValue(node, ctx) as ASTNode;

    expect(ctx.unres).toEqual([]);
    expect(ctx.errors).toEqual([]);
    expect(result).toMatchObject({
      k: "Comp",
      name: "Render",
      args: [{ k: "Str", v: "v" }, { k: "Comp", name: "Switch" }],
    });
    expect((result.args[1] as ASTNode & { k: "Comp" }).args[0]).toEqual({ k: "Ref", n: "v" });
  });

  it("materializes the two-variable form without unresolved refs", () => {
    const ctx = createMaterializeCtx();
    const node: ASTNode = {
      k: "Comp",
      name: "Render",
      args: [
        { k: "Str", v: "v" },
        { k: "Str", v: "row" },
        {
          k: "Comp",
          name: "Switch",
          args: [
            { k: "Ref", n: "v" },
            {
              k: "Member",
              obj: { k: "Ref", n: "row" },
              field: "statuses",
            },
            { k: "Str", v: "unknown" },
          ],
        },
      ],
    };

    const result = materializeValue(node, ctx) as ASTNode;
    const body = result.args[2] as ASTNode & { k: "Comp" };

    expect(ctx.unres).toEqual([]);
    expect(ctx.errors).toEqual([]);
    expect(body.args[0]).toEqual({ k: "Ref", n: "v" });
    expect(body.args[1]).toEqual({
      k: "Member",
      obj: { k: "Ref", n: "row" },
      field: "statuses",
    });
  });

  it("passes Render AST nodes through the evaluator unchanged", () => {
    const node: ASTNode = {
      k: "Comp",
      name: "Render",
      args: [
        { k: "Str", v: "v" },
        { k: "Comp", name: "Switch", args: [{ k: "Ref", n: "v" }, { k: "Obj", entries: [] }] },
      ],
    };

    expect(evaluate(node, evalContext)).toBe(node);
  });

  it("includes Render in prompts even when expressions are disabled", () => {
    const prompt = generatePrompt({
      ...basePromptSpec,
      toolCalls: false,
      bindings: false,
    });

    expect(prompt).toContain('@Render("v", expr)');
    expect(prompt).toContain("Dot member access:");
    expect(prompt).toContain("Index access:");
    expect(prompt).toContain("Ternary:");
    expect(prompt).not.toContain("@Count(array)");
  });
});
