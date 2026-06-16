import { useCallback, useState } from "react";

export interface UseGenerateResult {
  response: string;
  isStreaming: boolean;
  error: string | null;
  lastGenerateTime: number | null;
  generate: (prompt: string, dataModel?: Record<string, unknown>, systemPrompt?: string) => Promise<void>;
  replay: (dsl: string) => Promise<void>;
  reset: () => void;
}

// Playback cadence for replay() — how a canned DSL is streamed back to mimic a
// live generation. The demo content itself lives in ./replayDemo.
const REPLAY_CHUNK_SIZE = 32; // 每次"流出"的字符数
const REPLAY_INTERVAL_MS = 24; // 每个 chunk 之间的间隔

export function useGenerate(): UseGenerateResult {
  const [response, setResponse] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastGenerateTime, setLastGenerateTime] = useState<number | null>(null);

  const reset = useCallback(() => {
    setResponse("");
    setIsStreaming(false);
    setError(null);
    setLastGenerateTime(null);
  }, []);

  const generate = useCallback(async (prompt: string, dataModel?: Record<string, unknown>, systemPrompt?: string) => {
    setResponse("");
    setError(null);
    setIsStreaming(true);
    const startTime = performance.now();

    try {
      const res = await fetch("http://localhost:3001/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, dataModel, systemPrompt }),
      });

      if (!res.ok || !res.body) {
        const text = await res.text();
        throw new Error(text || `HTTP ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        setResponse((prev) => prev + chunk);
      }
      const trailing = decoder.decode();
      if (trailing) setResponse((prev) => prev + trailing);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Request failed";
      setError(msg);
    } finally {
      setIsStreaming(false);
      setLastGenerateTime(performance.now() - startTime);
    }
  }, []);

  // Simulate a streamed generation by emitting the given DSL in small chunks.
  const replay = useCallback(async (dsl: string) => {
    setResponse("");
    setError(null);
    setIsStreaming(true);
    const startTime = performance.now();
    for (let i = REPLAY_CHUNK_SIZE; i < dsl.length; i += REPLAY_CHUNK_SIZE) {
      setResponse(dsl.slice(0, i));
      await new Promise((resolve) => setTimeout(resolve, REPLAY_INTERVAL_MS));
    }
    setResponse(dsl);
    setIsStreaming(false);
    setLastGenerateTime(performance.now() - startTime);
  }, []);

  return { response, isStreaming, error, lastGenerateTime, generate, replay, reset };
}
