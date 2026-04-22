// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const mockPrompt = vi.fn();
const mockParse = vi.fn(() => ({ root: "ok" }));

vi.mock("@openuidev/react-lang", () => ({
  Renderer: () => <div>rendered</div>,
  createParser: () => ({ parse: mockParse }),
}));

vi.mock("@openuidev/react-ui-dsl", () => ({
  dslLibrary: {
    prompt: (...args: unknown[]) => mockPrompt(...args),
  },
}));

vi.mock("./useGenerate", () => ({
  useGenerate: () => ({
    response: "",
    isStreaming: false,
    error: null,
    generate: vi.fn(),
    reset: vi.fn(),
  }),
}));

describe("App system prompt tab", () => {
  beforeEach(() => {
    localStorage.clear();
    mockPrompt.mockReset();
    mockParse.mockClear();
    mockPrompt.mockImplementation((options?: { dataModel?: { raw: Record<string, unknown> } }) =>
      options?.dataModel ? `system:${JSON.stringify(options.dataModel.raw)}` : "system:{}",
    );
  });

  afterEach(() => {
    cleanup();
  });

  it("shows generated system prompt in the prompt tab and updates from data model before edits", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "prompt" }));

    const promptEditor = await screen.findByRole("textbox", { name: "System Prompt" });
    expect((promptEditor as HTMLTextAreaElement).value).toBe("system:{}");

    fireEvent.change(screen.getByLabelText("Data Model"), {
      target: { value: '{"region":"APAC"}' },
    });

    await waitFor(() => {
      expect((promptEditor as HTMLTextAreaElement).value).toBe('system:{"region":"APAC"}');
    });
  });

  it("keeps a manual system prompt edit when data model changes afterward", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "prompt" }));

    const promptEditor = await screen.findByRole("textbox", { name: "System Prompt" });
    fireEvent.change(promptEditor, { target: { value: "custom system prompt" } });

    fireEvent.change(screen.getByLabelText("Data Model"), {
      target: { value: '{"region":"EMEA"}' },
    });

    await waitFor(() => {
      expect((promptEditor as HTMLTextAreaElement).value).toBe("custom system prompt");
    });
  });
});
