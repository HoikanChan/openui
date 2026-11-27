// @vitest-environment jsdom
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CardView } from ".";

describe("Card renderer", () => {
  it("defaults to a column flex container", () => {
    const { container } = render(<CardView>Body copy</CardView>);
    const element = container.querySelector<HTMLDivElement>("div");

    expect(element?.style.flexDirection).toBe("column");
    expect(element?.textContent).toBe("Body copy");
  });

  it("maps contract flex props onto the container", () => {
    const { container } = render(
      <CardView align="center" direction="row" justify="between" wrap>
        Body copy
      </CardView>,
    );
    const element = container.querySelector<HTMLDivElement>("div");

    expect(element?.style.flexDirection).toBe("row");
    expect(element?.style.alignItems).toBe("center");
    expect(element?.style.justifyContent).toBe("space-between");
    expect(element?.style.flexWrap).toBe("wrap");
  });
});
