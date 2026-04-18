import cors from "cors";
import express from "express";
import { HttpsProxyAgent } from "https-proxy-agent";
import OpenAI from "openai";
import { buildSystemPrompt } from "./systemPrompt.js";

if (!process.env.LLM_API_KEY) {
  console.error("[server] LLM_API_KEY is not set. Please copy .env.example to .env and fill in your key.");
  process.exit(1);
}

const app = express();
const PORT = 3001;

app.use(cors({ origin: "http://localhost:5173" }));
app.use(express.json());

const httpAgent = process.env.HTTPS_PROXY
  ? new HttpsProxyAgent(process.env.HTTPS_PROXY)
  : undefined;

const openai = new OpenAI({
  apiKey: process.env.LLM_API_KEY,
  baseURL: process.env.LLM_BASE_URL,
  httpAgent,
});

app.post("/api/generate", async (req, res) => {
  const { prompt, dataModel } = req.body as { prompt: string; dataModel?: Record<string, unknown> };

  if (!prompt?.trim()) {
    res.status(400).json({ error: "prompt is required" });
    return;
  }

  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache");

  let started = false;

  try {
    const stream = await openai.chat.completions.create({
      model: process.env.LLM_MODEL ?? "deepseek-chat",
      messages: [
        { role: "system", content: buildSystemPrompt(dataModel) },
        { role: "user", content: prompt },
      ],
      stream: true,
    });

    for await (const chunk of stream) {
      const text = chunk.choices[0]?.delta?.content ?? "";
      if (text) {
        res.write(text);
        started = true;
      }
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
