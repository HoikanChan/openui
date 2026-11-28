import { describe, expect, it } from "vitest";

import { parseExpression } from "../parser/expressions";
import { tokenize } from "../parser/lexer";
import { parse } from "../parser/parser";
import { T } from "../parser/tokens";
import type { ParamMap } from "../parser/types";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

const schema: ParamMap = new Map([["Title", { params: [{ name: "text", required: true }] }]]);

const data: Record<string, unknown> = {
  owners: ["Alice", null, "Bob", undefined, 42, true],
  holes: [null, undefined],
  emptyList: [],
  empty: null,
  text: "already text",
  obj: { a: 1 },
  durationMinutes: 45,
  zero: 0,
  yes: true,
  no: false,
};

function evalExpr(src: string) {
  const tokens = tokenize(src).filter((t) => t.t !== T.Newline);
  const ast = parseExpression(tokens);
  const ctx: EvaluationContext = {
    getState: () => null,
    resolveRef: (name) => (name === "data" ? data : null),
  };
  return evaluate(ast, ctx);
}

describe("Join and String registration", () => {
  it("accepts both as builtins instead of reporting unknown-component", () => {
    for (const src of [
      'root = Title(@Join(data.owners, " / "))',
      'root = Title(@String(data.durationMinutes) + " min")',
    ]) {
      const codes = parse(src, schema).meta.errors.map((error) => error.code);
      expect(codes).not.toContain("unknown-component");
    }
  });
});

describe("Join builtin", () => {
  it("uses the default comma-space separator and stringifies every non-null element", () => {
    expect(evalExpr("@Join(data.owners)")).toBe("Alice, Bob, 42, true");
  });

  it("uses a custom separator", () => {
    expect(evalExpr('@Join(data.owners, " / ")')).toBe("Alice / Bob / 42 / true");
  });

  it("supports an empty separator", () => {
    expect(evalExpr('@Join(["a", "b"], "")')).toBe("ab");
  });

  it("returns an empty string when every element is skipped", () => {
    expect(evalExpr("@Join(data.holes)")).toBe("");
  });

  it("returns an empty string for empty, null, non-array, and object input", () => {
    expect(evalExpr("@Join(data.emptyList)")).toBe("");
    expect(evalExpr("@Join(data.empty)")).toBe("");
    expect(evalExpr("@Join(data.text)")).toBe("");
    expect(evalExpr("@Join(data.durationMinutes)")).toBe("");
    expect(evalExpr("@Join(data.obj)")).toBe("");
  });
});

describe("String builtin", () => {
  it("converts numbers to strings", () => {
    expect(evalExpr("@String(data.durationMinutes)")).toBe("45");
    expect(evalExpr("@String(data.zero)")).toBe("0");
  });

  it("converts booleans to strings", () => {
    expect(evalExpr("@String(data.yes)")).toBe("true");
    expect(evalExpr("@String(data.no)")).toBe("false");
  });

  it("returns an empty string for unsupported types", () => {
    expect(evalExpr("@String(data.text)")).toBe("");
    expect(evalExpr("@String(data.empty)")).toBe("");
    expect(evalExpr("@String(data.owners)")).toBe("");
    expect(evalExpr("@String(data.obj)")).toBe("");
  });

  it("composes with concatenation for display text", () => {
    expect(evalExpr('@String(data.durationMinutes) + " min"')).toBe("45 min");
  });
});
