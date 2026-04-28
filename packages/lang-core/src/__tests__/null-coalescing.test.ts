import { describe, expect, it } from "vitest";

import { parseExpression } from "../parser/expressions";
import { tokenize } from "../parser/lexer";
import { T } from "../parser/tokens";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

function evalExpr(src: string, data: Record<string, unknown>) {
  const tokens = tokenize(src).filter((t) => t.t !== T.Newline);
  const ast = parseExpression(tokens);
  const ctx: EvaluationContext = {
    getState: () => null,
    resolveRef: (name) => (name in data ? (data[name] as unknown) : null),
  };
  return evaluate(ast, ctx);
}

describe("?? null-coalescing operator — runtime", () => {
  it("null left side returns right side", () => {
    expect(evalExpr('v ?? "—"', { v: null })).toBe("—");
  });

  it("undefined left side returns right side", () => {
    expect(evalExpr('v ?? "—"', { v: undefined })).toBe("—");
  });

  it("0 left side returns 0, not right side", () => {
    expect(evalExpr("v ?? 99", { v: 0 })).toBe(0);
  });

  it("false left side returns false, not right side", () => {
    expect(evalExpr("v ?? true", { v: false })).toBe(false);
  });

  it("empty string left side returns empty string, not right side", () => {
    expect(evalExpr('v ?? "default"', { v: "" })).toBe("");
  });

  it("non-null string left side returns left side", () => {
    expect(evalExpr('v ?? "fallback"', { v: "present" })).toBe("present");
  });

  it("chained ?? is left-associative: first non-null wins", () => {
    expect(evalExpr('v ?? w ?? "end"', { v: null, w: "middle" })).toBe("middle");
    expect(evalExpr('v ?? w ?? "end"', { v: "first", w: null })).toBe("first");
    expect(evalExpr('v ?? w ?? "end"', { v: null, w: null })).toBe("end");
  });

  it("DSL use case: null field with string concat", () => {
    expect(evalExpr('(v ?? "—") + "核"', { v: null })).toBe("—核");
  });

  it("DSL use case: non-null field with string concat", () => {
    expect(evalExpr('(v ?? "—") + "核"', { v: 8 })).toBe("8核");
  });
});
