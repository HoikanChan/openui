// @vitest-environment jsdom
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ImageBlockSchema } from "./schema";
import { ImageBlockView } from "./view";

describe("ImageBlock", () => {
  it("requires src and accepts an optional alt", () => {
    expect(ImageBlockSchema.safeParse({ src: "/hero.png" }).success).toBe(true);
    expect(ImageBlockSchema.safeParse({ src: "/hero.png", alt: "Hero" }).success).toBe(true);
    expect(ImageBlockSchema.safeParse({ alt: "Hero" }).success).toBe(false);
  });

  it("shows a loading skeleton until the image loads", () => {
    const { container } = render(<ImageBlockView alt="Hero" src="/hero.png" />);
    const image = container.querySelector("img");

    expect(container.querySelector(".ant-skeleton")).not.toBeNull();
    expect(image?.getAttribute("src")).toBe("/hero.png");
    expect(image?.getAttribute("alt")).toBe("Hero");
  });
});
