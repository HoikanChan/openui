import cors from "cors";
import express from "express";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";
import { resolveSystemPrompt } from "./systemPrompt.js";

if (!process.env.LLM_API_KEY) {
  console.error("[server] LLM_API_KEY is not set. Please copy .env.example to .env and fill in your key.");
  process.exit(1);
}

const app = express();
const PORT = 3001;

app.use(cors({ origin: "*" }));
app.use(express.json());

function shouldBypassProxy(baseURL: string | undefined): boolean {
  if (!baseURL) return false;
  const host = new URL(baseURL).hostname;
  return (process.env.NO_PROXY ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .some((entry) => host === entry || host.endsWith(`.${entry}`));
}

const httpAgent = process.env.HTTPS_PROXY && !shouldBypassProxy(process.env.LLM_BASE_URL)
  ? new HttpsProxyAgent(process.env.HTTPS_PROXY)
  : undefined;

const openai = new OpenAI({
  apiKey: process.env.LLM_API_KEY,
  baseURL: process.env.LLM_BASE_URL,
  httpAgent,
});

app.post("/api/generate", async (req, res) => {
  const { prompt, dataModel, systemPrompt } = req.body as {
    prompt: string;
    dataModel?: Record<string, unknown>;
    systemPrompt?: string;
  };

  if (!prompt?.trim()) {
    res.status(400).json({ error: "prompt is required" });
    return;
  }

  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache");

  let started = false;

  try {
    const model = process.env.LLM_MODEL ?? "deepseek-chat";
    const stream = await openai.chat.completions.create({
      model,
      messages: [
        { role: "system", content: resolveSystemPrompt(systemPrompt, dataModel) },
        { role: "user", content: prompt },
      ],
      max_tokens: 8192,
      stream: true,
      stream_options: { include_usage: true },
      // deepseek-v4-* defaults to thinking mode; DSL generation doesn't need it
      // and it adds seconds of hidden reasoning before the first visible token.
      ...(model.startsWith("deepseek-v4") ? { thinking: { type: "disabled" } } : {}),
    } as OpenAI.Chat.ChatCompletionCreateParamsStreaming);

    let finishReason: string | null = null;
    for await (const chunk of stream) {
      const choice = chunk.choices[0];
      const text = choice?.delta?.content ?? "";
      if (text) {
        res.write(text);
        started = true;
      }
      if (choice?.finish_reason) {
        finishReason = choice.finish_reason;
      }
      if (chunk.usage) {
        console.log("[server] usage:", JSON.stringify(chunk.usage));
      }
    }
    console.log("[server] finish_reason =", finishReason ?? "(none — connection dropped)");
    if (finishReason !== "stop") {
      // 流没有以 finish_reason="stop" 收尾:null 表示连接被掐断(代理/网络),"length" 表示 token 上限截断
      console.warn("[server] stream ended abnormally, finish_reason =", finishReason);
      res.write(`\n\n[ERROR: incomplete, finish_reason=${finishReason ?? "connection dropped"}]`);
    }
  } catch (err) {
    const msg = err instanceof Error ? err.message : "OpenAI error";
    console.error("[server] OpenAI error:", msg);
    if (!started) {
      res.status(502).json({ error: msg });
      return;
    }
    res.write(`\n\n[ERROR: ${msg}]`);
  } finally {
    res.end();
  }
});

app.listen(PORT, () => {
  console.log(`[server] listening on http://localhost:${PORT}`);
});
