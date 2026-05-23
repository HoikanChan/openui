import React from "react";
import { describe, expect, it } from "vitest";
import { dslLibrary } from "../dslLibrary";
import { Stack } from ".";
import { StackSchema } from "./schema";
import { StackView } from "./view";

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
    expect(
      StackSchema.safeParse({ direction: "diagonal" }).success,
    ).toBe(false);
  });

  it("rejects numeric gap", () => {
    expect(
      StackSchema.safeParse({ gap: 16 }).success,
    ).toBe(false);
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
    expect(rendered.type).toBe(StackView);
    expect(rendered.props.vertical).toBe(true);
    expect(rendered.props.gap).toBe(12);
  });

  it("renders row direction when direction is row", () => {
    const rendered = Stack.component({
      props: { children: ["A"], direction: "row", gap: "l" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.type).toBe(StackView);
    expect(rendered.props.vertical).toBe(false);
    expect(rendered.props.gap).toBe(18);
  });

  it("maps gap token l to 18px", () => {
    const rendered = Stack.component({
      props: { gap: "l" },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.gap).toBe(18);
  });

  it("passes wrap prop through", () => {
    const rendered = Stack.component({
      props: { wrap: true },
      renderNode: (v) => v as React.ReactNode,
    });
    expect(rendered.props.wrap).toBe(true);
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
