import React from "react";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";
import { Stack, StackSchema } from "./index";

describe("Stack schema", () => {
  it("accepts valid props", () => {
    const result = StackSchema.safeParse({
      children: [],
      direction: "row",
      gap: "l",
      align: "center",
      justify: "between",
      wrap: true,
    });
    expect(result.success).toBe(true);
  });

  it("defaults direction to column when omitted", () => {
    const result = StackSchema.safeParse({ children: [] });
    expect(result.success).toBe(true);
  });

  it("rejects invalid direction value", () => {
    expect(StackSchema.safeParse({ direction: "diagonal" }).success).toBe(false);
  });

  it("rejects numeric gap", () => {
    expect(StackSchema.safeParse({ gap: 16 }).success).toBe(false);
  });

  it("keeps direction as second positional arg in signature", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.Stack.signature).toContain('Stack(children?: any[], direction?: "row" | "column"');
  });
});

describe("Stack renderer", () => {
  it("renders column direction by default", () => {
    const rendered = Stack.component({
      props: { children: ["A"], gap: "m" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.type).toBe("div");
    expect(rendered.props.style.flexDirection).toBe("column");
    expect(rendered.props.style.gap).toBe("0.75rem");
  });

  it("renders row direction when direction is row", () => {
    const rendered = Stack.component({
      props: { children: ["A"], direction: "row", gap: "l" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.type).toBe("div");
    expect(rendered.props.style.flexDirection).toBe("row");
    expect(rendered.props.style.gap).toBe("1.125rem");
  });

  it("maps gap token l to 1.125rem", () => {
    const rendered = Stack.component({
      props: { gap: "l" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.gap).toBe("1.125rem");
  });

  it("defaults gap to 0.75rem when not specified", () => {
    const rendered = Stack.component({
      props: {},
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.gap).toBe("0.75rem");
  });

  it("sets flexWrap when wrap is true", () => {
    const rendered = Stack.component({
      props: { wrap: true },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.flexWrap).toBe("wrap");
  });

  it("falls back justifyContent to flex-start when wrap=true and justify=between", () => {
    const rendered = Stack.component({
      props: { wrap: true, justify: "between" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.justifyContent).toBe("flex-start");
  });

  it("keeps justify=between as space-between when wrap is not set", () => {
    const rendered = Stack.component({
      props: { justify: "between" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.style.justifyContent).toBe("space-between");
  });
});

describe("Stack description", () => {
  it("matches exact spec description for LLM", () => {
    expect(Stack.description).toBe(
      'Flex container. direction: "row"|"column" (default "column"). gap: "none"|"xs"|"s"|"m"|"l"|"xl"|"2xl" (default "m"). align: "start"|"center"|"end"|"stretch"|"baseline". justify: "start"|"center"|"end"|"between"|"around"|"evenly".',
    );
  });

  it("description contains default column and default m", () => {
    expect(Stack.description).toContain('default "column"');
    expect(Stack.description).toContain('default "m"');
  });
});

describe("Stack library registration", () => {
  it("appears in dslLibrary components", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.Stack).toBeDefined();
  });

  it("is the root component", () => {
    const prompt = dslLibrary.prompt({ toolCalls: false, bindings: false });
    expect(prompt).toContain("root = Stack(");
    expect(prompt).not.toContain("root = VLayout(");
  });

  it("VLayout is not registered", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.VLayout).toBeUndefined();
  });

  it("HLayout is not registered", () => {
    const spec = dslLibrary.toSpec();
    expect(spec.components.HLayout).toBeUndefined();
  });
});
