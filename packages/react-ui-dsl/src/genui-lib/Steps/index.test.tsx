// @vitest-environment jsdom
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StepsItemSchema, StepsSchema } from "./schema";
import { StepItemView, StepsView } from "./view";

describe("Steps", () => {
  it("requires items made of StepsItem values", () => {
    expect(StepsSchema.safeParse({ items: [{ title: "Install" }] }).success).toBe(true);
    expect(
      StepsSchema.safeParse({ items: [{ title: "Install", details: "Run pnpm install" }] }).success,
    ).toBe(true);
    expect(StepsSchema.safeParse({ items: [] }).success).toBe(true);
    expect(StepsSchema.safeParse({}).success).toBe(false);
    expect(StepsItemSchema.safeParse({}).success).toBe(false);
  });

  it("renders an ordered list of steps", () => {
    const { container } = render(
      <StepsView>
        <StepItemView details={<span>Run pnpm install</span>} title="Install" />
      </StepsView>,
    );

    expect(container.querySelector("ol")).not.toBeNull();
    expect(container.querySelector("li")?.textContent).toContain("Install");
    expect(container.textContent).toContain("Run pnpm install");
  });
});
