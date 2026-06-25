// @vitest-environment jsdom

import { act, cleanup, render, screen } from "@testing-library/react";
import { defineComponent } from "@openuidev/react-lang";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { z } from "zod";
import { dslLibrary } from "../genui-lib/dslLibrary.js";
import { canvasStore } from "../canvas/canvasStore.js";
import { useDslRuntime, useLibrary, useToolProvider } from "./useDslRuntime";

const RuntimeBadge = defineComponent({
  name: "RuntimeHookBadge",
  props: z.object({ text: z.string() }),
  description: "Runtime hook extension badge",
  component: () => null,
});

function Probe() {
  const library = useLibrary();
  const toolProvider = useToolProvider();
  const runtime = useDslRuntime();

  return (
    <div>
      <span data-testid="library-root">{library.root}</span>
      <span data-testid="runtime-root">{runtime.library.root}</span>
      <span data-testid="has-extension">
        {library.components.RuntimeHookBadge ? "yes" : "no"}
      </span>
      <span data-testid="tool-names">{Object.keys(toolProvider).join(",")}</span>
    </div>
  );
}

describe("DSL runtime hooks", () => {
  beforeEach(() => {
    canvasStore.clear();
    canvasStore.resetRuntime();
  });

  afterEach(() => {
    cleanup();
  });

  it("falls back to dslLibrary before any PIU extension registers", () => {
    render(<Probe />);

    expect(screen.getByTestId("library-root").textContent).toBe(dslLibrary.root);
    expect(screen.getByTestId("runtime-root").textContent).toBe(dslLibrary.root);
    expect(screen.getByTestId("has-extension").textContent).toBe("no");
    expect(screen.getByTestId("tool-names").textContent).toBe("");
  });

  it("updates subscribers when a PIU extension registers components and tools", () => {
    render(<Probe />);

    act(() => {
      canvasStore.addExtension({
        extensionId: "alarm-biz",
        extension: {
          components: [RuntimeBadge],
          tools: ["queryAlarms"],
        },
        toolProvider: { queryAlarms: async () => ({ total: 1 }) },
      });
    });

    expect(screen.getByTestId("library-root").textContent).toBe(dslLibrary.root);
    expect(screen.getByTestId("runtime-root").textContent).toBe(dslLibrary.root);
    expect(screen.getByTestId("has-extension").textContent).toBe("yes");
    expect(screen.getByTestId("tool-names").textContent).toBe("queryAlarms");
  });
});
