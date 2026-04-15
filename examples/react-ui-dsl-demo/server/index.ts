import cors from "cors";
import express from "express";
import OpenAI from "openai";
import { systemPrompt } from "./systemPrompt.js";

if (!process.env.OPENAI_API_KEY) {
  console.error("[server] OPENAI_API_KEY is not set. Please copy .env.example to .env and fill in your key.");
  process.exit(1);
}

const app = express();
const PORT = 3001;

app.use(cors({ origin: "http://localhost:5173" }));
app.use(express.json());

const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

app.post("/api/generate", async (req, res) => {
  const { prompt } = req.body as { prompt: string };

  if (!prompt?.trim()) {
    res.status(400).json({ error: "prompt is required" });
    return;
  }

  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache");

  let started = false;

  try {
    const stream = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        { role: "system", content: systemPrompt },
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
