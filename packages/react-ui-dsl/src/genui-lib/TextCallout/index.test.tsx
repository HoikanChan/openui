// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TextCalloutSchema } from "./schema";
import { resolveCalloutAppearance, TextCalloutView } from "./view";

describe("TextCallout", () => {
  it("accepts the contract variants and rejects unknown ones", () => {
    expect(TextCalloutSchema.safeParse({}).success).toBe(true);
    expect(TextCalloutSchema.safeParse({ variant: "warning" }).success).toBe(true);
    expect(TextCalloutSchema.safeParse({ variant: "critical" }).success).toBe(false);
  });

  it("defaults to the neutral appearance", () => {
    expect(resolveCalloutAppearance()).toEqual({
      accentColor: "#8c8c8c",
      backgroundColor: "#f5f5f5",
    });
    expect(resolveCalloutAppearance("danger").accentColor).toBe("#ff4d4f");
  });

  it("renders its title and description", () => {
    render(
      <TextCalloutView
        description="Retry before the cache expires."
        title="Heads up"
        variant="warning"
      />,
    );

    expect(screen.getByRole("note").textContent).toContain("Heads up");
    expect(screen.getByRole("note").textContent).toContain("Retry before the cache expires.");
  });
});
