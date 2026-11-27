// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ModalSchema } from "./schema";
import { ModalView } from "./view";

describe("Modal", () => {
  it("requires title and children and accepts the contract size variants", () => {
    expect(ModalSchema.safeParse({ title: "Edit profile", children: [] }).success).toBe(true);
    expect(ModalSchema.safeParse({ title: "Edit profile", children: [], size: "lg" }).success).toBe(
      true,
    );
    expect(ModalSchema.safeParse({ title: "Edit profile" }).success).toBe(false);
    expect(ModalSchema.safeParse({ title: "Edit profile", children: [], size: "xl" }).success).toBe(
      false,
    );
  });

  it("opens with the contract title and children", () => {
    render(
      <ModalView onClose={() => undefined} open title="Edit profile">
        <p>Body copy</p>
      </ModalView>,
    );

    expect(screen.getByText("Edit profile")).toBeTruthy();
    expect(screen.getByText("Body copy")).toBeTruthy();
  });
});
