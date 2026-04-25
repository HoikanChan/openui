import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ASTNode } from "../parser/ast";
import { BUILTINS, isBuiltin } from "../parser/builtins";
import { generatePrompt, type PromptSpec } from "../parser/prompt";
import { evaluate, type EvaluationContext } from "../runtime/evaluator";

const basePromptSpec: PromptSpec = {
  root: "Root",
  components: {
    Root: { signature: "Root(children: Component[])", description: "Root container" },
  },
};

function createContext(locale?: string): EvaluationContext {
  return {
    getState: () => null,
    resolveRef: () => null,
    builtinContext: locale ? { locale } : undefined,
  };
}

function evaluateBuiltin(
  name: string,
  args: ASTNode[],
  context: EvaluationContext = createContext(),
): unknown {
  return evaluate({ k: "Comp", name, args }, context);
}

describe("format builtins", () => {
  beforeEach(() => {
    vi.useRealTimers();
  });

  it("registers the shared formatting builtin family and exposes prompt docs", () => {
    expect(isBuiltin("FormatDate")).toBe(true);
    expect(isBuiltin("FormatBytes")).toBe(true);
    expect(isBuiltin("FormatNumber")).toBe(true);
    expect(isBuiltin("FormatPercent")).toBe(true);
    expect(isBuiltin("FormatDuration")).toBe(true);

    expect(BUILTINS.FormatDate?.signature).toContain("FormatDate(value, style?, locale?)");
    expect(BUILTINS.FormatBytes?.signature).toContain("FormatBytes(value)");
    expect(BUILTINS.FormatNumber?.signature).toContain("FormatNumber(value, decimals?, locale?)");
    expect(BUILTINS.FormatPercent?.signature).toContain("FormatPercent(value, decimals?, locale?)");
    expect(BUILTINS.FormatDuration?.signature).toContain("FormatDuration(value, unit?, locale?)");

    const prompt = generatePrompt({ ...basePromptSpec, bindings: true });
    expect(prompt).toContain("@FormatDate(value, style?, locale?)");
    expect(prompt).toContain("@FormatBytes(value)");
    expect(prompt).toContain("@FormatNumber(value, decimals?, locale?)");
    expect(prompt).toContain("@FormatPercent(value, decimals?, locale?)");
    expect(prompt).toContain("@FormatDuration(value, unit?, locale?)");
  });

  it("inherits renderer locale for numbers and allows explicit locale override", () => {
    const inherited = evaluateBuiltin(
      "FormatNumber",
      [{ k: "Num", v: 12345.67 }, { k: "Num", v: 2 }],
      createContext("de-DE"),
    );

    const overridden = evaluateBuiltin(
      "FormatNumber",
      [{ k: "Num", v: 12345.67 }, { k: "Num", v: 2 }, { k: "Str", v: "en-US" }],
      createContext("de-DE"),
    );

    expect(inherited).toBe(
      new Intl.NumberFormat("de-DE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(12345.67),
    );
    expect(overridden).toBe(
      new Intl.NumberFormat("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(12345.67),
    );
  });

  it("formats ratios as locale-aware percentages", () => {
    const formatted = evaluateBuiltin(
      "FormatPercent",
      [{ k: "Num", v: 0.125 }, { k: "Num", v: 1 }],
      createContext("fr-FR"),
    );

    expect(formatted).toBe(
      new Intl.NumberFormat("fr-FR", {
        style: "percent",
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(0.125),
    );
  });

  it("formats bytes with SI by default", () => {
    expect(
      evaluateBuiltin("FormatBytes", [{ k: "Num", v: 1536 }], createContext("en-US")),
    ).toBe("1.5 KB");
  });

  it("formats durations from seconds by default and supports millisecond input", () => {
    expect(
      evaluateBuiltin("FormatDuration", [{ k: "Num", v: 65 }], createContext("en-US")),
    ).toBe("1m 5s");

    expect(
      evaluateBuiltin(
        "FormatDuration",
        [{ k: "Num", v: 65000 }, { k: "Str", v: "ms" }],
        createContext("en-US"),
      ),
    ).toBe("1m 5s");
  });

  it("formats date presets and relative dates", () => {
    const value = "2026-01-02T03:04:05.000Z";
    const date = new Date(value);

    expect(
      evaluateBuiltin(
        "FormatDate",
        [{ k: "Str", v: value }, { k: "Str", v: "date" }, { k: "Str", v: "en-US" }],
      ),
    ).toBe(new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(date));

    expect(
      evaluateBuiltin(
        "FormatDate",
        [{ k: "Str", v: value }, { k: "Str", v: "time" }, { k: "Str", v: "en-US" }],
      ),
    ).toBe(new Intl.DateTimeFormat("en-US", { timeStyle: "short" }).format(date));

    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-01-02T04:04:05.000Z"));

    expect(
      evaluateBuiltin(
        "FormatDate",
        [{ k: "Str", v: value }, { k: "Str", v: "relative" }, { k: "Str", v: "en-US" }],
      ),
    ).toBe(new Intl.RelativeTimeFormat("en-US", { numeric: "auto" }).format(-1, "hour"));
  });

  it("fails soft for nullish and invalid values", () => {
    expect(evaluateBuiltin("FormatDate", [{ k: "Null" }])).toBe("");
    expect(evaluateBuiltin("FormatBytes", [{ k: "Null" }])).toBe("");
    expect(evaluateBuiltin("FormatNumber", [{ k: "Null" }])).toBe("");
    expect(evaluateBuiltin("FormatPercent", [{ k: "Null" }])).toBe("");
    expect(evaluateBuiltin("FormatDuration", [{ k: "Null" }])).toBe("");

    expect(
      evaluateBuiltin("FormatDate", [{ k: "Str", v: "not-a-date" }, { k: "Str", v: "date" }]),
    ).toBe("not-a-date");
    expect(evaluateBuiltin("FormatBytes", [{ k: "Str", v: "abc" }])).toBe("abc");
    expect(evaluateBuiltin("FormatDuration", [{ k: "Str", v: "abc" }])).toBe("abc");
  });
});
