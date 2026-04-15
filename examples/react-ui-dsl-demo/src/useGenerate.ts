import { useCallback, useState } from "react";

export interface UseGenerateResult {
  response: string;
  isStreaming: boolean;
  error: string | null;
  generate: (prompt: string) => Promise<void>;
  reset: () => void;
}

export function useGenerate(): UseGenerateResult {
  const [response, setResponse] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = useCallback(() => {
    setResponse("");
    setIsStreaming(false);
    setError(null);
  }, []);

  const generate = useCallback(async (prompt: string) => {
    setResponse("");
    setError(null);
    setIsStreaming(true);

    try {
      const res = await fetch("http://localhost:3001/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt }),
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
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Request failed";
      setError(msg);
    } finally {
      setIsStreaming(false);
    }
  }, []);

  return { response, isStreaming, error, generate, reset };
}
