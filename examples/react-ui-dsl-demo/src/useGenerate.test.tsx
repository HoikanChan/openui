// @vitest-environment jsdom
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useGenerate } from "./useGenerate";

function createStream(text: string) {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(text));
      controller.close();
    },
  });
}

describe("useGenerate", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("sends systemPrompt in the generate request body", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      body: createStream("root = View([])"),
      text: vi.fn(),
    } as unknown as Response);

    const { result } = renderHook(() => useGenerate());

    await act(async () => {
      await result.current.generate("user prompt", { region: "APAC" }, "custom system prompt");
    });

    await waitFor(() => {
      expect(result.current.response).toBe("root = View([])");
    });

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:3001/api/generate",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          prompt: "user prompt",
          dataModel: { region: "APAC" },
          systemPrompt: "custom system prompt",
        }),
      }),
    );
  });
});
