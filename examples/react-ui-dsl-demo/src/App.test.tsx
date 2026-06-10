// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const { mockParse, mockSchema, mockCreateParser } = vi.hoisted(() => {
  const parse = vi.fn(() => ({ root: "ok" }));
  return {
    mockParse: parse,
    mockSchema: { $defs: { Stack: { properties: {}, required: [] } } },
    mockCreateParser: vi.fn(() => ({ parse })),
  };
});

vi.mock("@openuidev/react-lang", () => ({
  Renderer: () => <div>rendered</div>,
  createParser: mockCreateParser,
  defineComponent: (config: unknown) => config,
}));

vi.mock("@openuidev/react-ui-dsl", () => ({
  dslLibrary: {
    toJSONSchema: () => mockSchema,
    // extensions.tsx 在模块加载时构建扩展库
    extend: () => ({ toJSONSchema: () => mockSchema }),
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

/** 模拟 GenUI Service:GET /contexts 返回种子,POST /prompts/assemble 回显 dataModel。 */
function stubGenuiServiceFetch() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      const href = String(url);
      if (href.endsWith("/v1/contexts")) {
        return {
          ok: true,
          json: async () => [{ contextId: "noe-alarm-tools", version: "1.0.0" }],
        } as unknown as Response;
      }
      if (href.endsWith("/v1/prompts/assemble")) {
        const body = JSON.parse(String(init?.body ?? "{}")) as {
          contextId?: string;
          dataModel?: { raw: Record<string, unknown> };
        };
        const prefix = body.contextId ? `system[${body.contextId}]:` : "system:";
        return {
          ok: true,
          json: async () => ({ prompt: `${prefix}${JSON.stringify(body.dataModel?.raw ?? {})}` }),
        } as unknown as Response;
      }
      throw new Error(`unexpected fetch: ${href}`);
    }),
  );
}

describe("App system prompt tab", () => {
  beforeEach(() => {
    localStorage.clear();
    mockParse.mockClear();
    mockCreateParser.mockClear();
    stubGenuiServiceFetch();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows service-assembled prompt in the prompt tab and updates from data model before edits", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Expand source panel" }));
    fireEvent.click(screen.getByRole("button", { name: "prompt" }));

    const promptEditor = await screen.findByRole("textbox", { name: "System Prompt" });
    await waitFor(() => {
      expect((promptEditor as HTMLTextAreaElement).value).toBe("system:{}");
    });

    fireEvent.change(screen.getByLabelText("Data Model"), {
      target: { value: '{"region":"APAC"}' },
    });

    await waitFor(() => {
      expect((promptEditor as HTMLTextAreaElement).value).toBe('system:{"region":"APAC"}');
    });
  });

  it("re-assembles the prompt when a Generation Context is selected", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Expand source panel" }));
    fireEvent.click(screen.getByRole("button", { name: "prompt" }));
    const promptEditor = await screen.findByRole("textbox", { name: "System Prompt" });

    const contextSelect = await screen.findByLabelText("Context");
    await waitFor(() => {
      expect(screen.getByRole("option", { name: /noe-alarm-tools/ })).toBeDefined();
    });
    fireEvent.change(contextSelect, { target: { value: "noe-alarm-tools" } });

    await waitFor(() => {
      expect((promptEditor as HTMLTextAreaElement).value).toBe("system[noe-alarm-tools]:{}");
    });
  });

  it("keeps a manual system prompt edit when data model changes afterward", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Expand source panel" }));
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

  it("allows entering open lang before generating", async () => {
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Expand source panel" }));
    const langEditor = screen.getByRole("textbox", { name: "Open Lang" });
    fireEvent.change(langEditor, { target: { value: "root = Stack([])" } });

    await waitFor(() => {
      expect(mockCreateParser).toHaveBeenCalledWith(mockSchema);
      expect(mockParse).toHaveBeenCalledWith("root = Stack([])");
    });
  });
});
