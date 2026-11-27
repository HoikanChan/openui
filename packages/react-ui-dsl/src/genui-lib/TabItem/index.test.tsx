// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TabItemSchema } from "./schema";
import { TabItemView } from "./view";

describe("TabItem", () => {
  it("requires value, label, and content", () => {
    expect(TabItemSchema.safeParse({ value: "a", label: "A", content: [] }).success).toBe(true);
    expect(TabItemSchema.safeParse({ value: "a", label: "A" }).success).toBe(false);
    expect(TabItemSchema.safeParse({ value: "a", content: [] }).success).toBe(false);
  });

  it("renders content in a panel labelled by the tab label", () => {
    render(
      <TabItemView label="Overview" value="overview">
        <p>Body copy</p>
      </TabItemView>,
    );

    expect(screen.getByRole("tabpanel", { name: "Overview" }).textContent).toContain("Body copy");
  });
});
