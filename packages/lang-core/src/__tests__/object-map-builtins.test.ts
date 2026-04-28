import { describe, expect, it } from "vitest";

import type { ASTNode } from "../parser/ast";
import { BUILTINS, isBuiltin } from "../parser/builtins";
import { generatePrompt, type PromptSpec } from "../parser/prompt";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

const runtime = {};

const basePromptSpec: PromptSpec = {
  root: "Root",
  components: {
    Root: { signature: "Root(children: Component[])", description: "Root container" },
  },
};

const evalContext: EvaluationContext = {
  getState: () => null,
  resolveRef: () => null,
};

function evaluateBuiltin(name: string, args: ASTNode[]): unknown {
  return evaluate({ k: "Comp", name, args }, evalContext);
}

describe("object map builtins", () => {
  it("registers ObjectEntries and ObjectKeys and exposes prompt docs", () => {
    expect(isBuiltin("ObjectEntries")).toBe(true);
    expect(isBuiltin("ObjectKeys")).toBe(true);

    expect(BUILTINS.ObjectEntries?.signature).toContain("ObjectEntries(obj)");
    expect(BUILTINS.ObjectKeys?.signature).toContain("ObjectKeys(obj)");

    const prompt = generatePrompt({ ...basePromptSpec, bindings: true });
    expect(prompt).toContain("@ObjectEntries(obj) -> {key: string, value: any}[]");
    expect(prompt).toContain("@ObjectKeys(obj) -> string[]");
  });

  it("converts a plain object map into ordered entry rows", () => {
    const entriesFn = BUILTINS.ObjectEntries.fn;

    expect(
      entriesFn(runtime, {
        "dev-001": { status: "up" },
        "dev-002": { status: "down" },
      }),
    ).toEqual([
      { key: "dev-001", value: { status: "up" } },
      { key: "dev-002", value: { status: "down" } },
    ]);
  });

  it("returns ordered keys for plain object maps", () => {
    const keysFn = BUILTINS.ObjectKeys.fn;

    expect(
      keysFn(runtime, {
        "rack-01": { load: 0.7 },
        "rack-02": { load: 0.4 },
      }),
    ).toEqual(["rack-01", "rack-02"]);
  });

  it("fails soft for null, arrays, and unsupported objects", () => {
    const entriesFn = BUILTINS.ObjectEntries.fn;
    const keysFn = BUILTINS.ObjectKeys.fn;

    expect(entriesFn(runtime, null)).toEqual([]);
    expect(entriesFn(runtime, [1, 2])).toEqual([]);
    expect(entriesFn(runtime, "bad")).toEqual([]);
    expect(entriesFn(runtime, new Date("2026-01-01T00:00:00.000Z"))).toEqual([]);

    expect(keysFn(runtime, null)).toEqual([]);
    expect(keysFn(runtime, [1, 2])).toEqual([]);
    expect(keysFn(runtime, 42)).toEqual([]);
    expect(keysFn(runtime, new Date("2026-01-01T00:00:00.000Z"))).toEqual([]);
  });

  it("evaluates through the shared runtime dispatcher", () => {
    const entries = evaluateBuiltin("ObjectEntries", [
      {
        k: "Obj",
        entries: [
          ["svc-a", { k: "Obj", entries: [["status", { k: "Str", v: "up" }]] }],
          ["svc-b", { k: "Obj", entries: [["status", { k: "Str", v: "down" }]] }],
        ],
      },
    ]);

    const keys = evaluateBuiltin("ObjectKeys", [
      {
        k: "Obj",
        entries: [
          ["svc-a", { k: "Num", v: 1 }],
          ["svc-b", { k: "Num", v: 2 }],
        ],
      },
    ]);

    expect(entries).toEqual([
      { key: "svc-a", value: { status: "up" } },
      { key: "svc-b", value: { status: "down" } },
    ]);
    expect(keys).toEqual(["svc-a", "svc-b"]);
  });
});
