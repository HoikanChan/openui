---
title: react-ui-dsl Demo Example Design
date: 2026-04-15
status: approved
---

# react-ui-dsl Demo Example

## Background

The `packages/react-ui-dsl` package wraps the company's enterprise React components in the `react-lang` DSL runtime. Before the library implementation is complete, we need a working example app that demonstrates the full end-to-end flow: user types a prompt → LLM generates DSL → UI renders live.

This example is the canonical reference for how consumers integrate `react-ui-dsl` with the `react-lang` Renderer.

## Decision

A new example at `examples/react-ui-dsl-demo/` — a Vite + React frontend paired with a Node.js Express backend, both in the same folder, started together via `concurrently`.

The frontend fetches the Express server directly (CORS enabled on the server). No Vite proxy needed.

## Layout

Two-column split layout:

```
┌──────────────────────┬─────────────────┐
│  DSL code viewer     │  Prompt input   │
│  (streams live)      │                 │
├──────────────────────│  dataModel JSON │
│  PREVIEW             │  input          │
│  <Renderer> output   │                 │
│                      │  [Generate]     │
└──────────────────────┴─────────────────┘
```

- **Left column, top**: Read-only code viewer showing raw DSL as it streams in from the LLM
- **Left column, bottom**: Live `<Renderer>` preview — updates as DSL accumulates
- **Right column**: Prompt textarea, dataModel JSON textarea, Generate button

## Package Structure

```
examples/react-ui-dsl-demo/
├── server/
│   ├── index.ts          # Express server — POST /api/generate, streams DSL
│   └── systemPrompt.ts   # Builds system prompt from dslLibrary.toSystemPrompt()
├── src/
│   ├── main.tsx          # Vite app entry
│   ├── App.tsx           # 2-column layout, wires useGenerate → Renderer
│   └── useGenerate.ts    # fetch + streaming state: response, isStreaming, error
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## Tech Stack

- **Frontend**: Vite + React 19 + TypeScript
- **Backend**: Express + `cors` + `openai` SDK (streaming)
- **DSL rendering**: `@openuidev/react-lang` Renderer + `@openuidev/react-ui-dsl` dslLibrary
- **Dev runner**: `concurrently` — one `pnpm dev` starts both

## Data Flow

1. User fills in prompt (and optionally a dataModel JSON object) → clicks Generate
2. `useGenerate` fires `POST http://localhost:3001/api/generate` with `{ prompt }`
3. Express calls OpenAI with the DSL system prompt (from `dslLibrary.toSystemPrompt()`) + user prompt, response mode: streaming
4. Express pipes OpenAI chunks directly to the HTTP response as `text/plain`
5. `useGenerate` reads the `ReadableStream` chunk by chunk, appending to `response` string, `isStreaming: true`
6. `<Renderer response={response} library={dslLibrary} isStreaming={isStreaming} dataModel={parsedDataModel} />` re-renders on each chunk
7. Stream ends → `isStreaming` flips to `false`, Renderer finalizes

## Server (`server/index.ts`)

- Port: `3001`
- `cors()` middleware — allows `http://localhost:5173`
- `POST /api/generate` — body: `{ prompt: string }`
  - Builds system prompt via `dslLibrary.toSystemPrompt()`
  - Calls OpenAI chat completions with `stream: true`
  - Sets response header `Content-Type: text/plain; charset=utf-8`
  - Pipes each text delta directly to the response
  - Ends response when stream completes

## Client (`useGenerate.ts`)

Manages three state values:
- `response: string` — accumulated DSL string, passed to `<Renderer>`
- `isStreaming: boolean` — true while reading chunks
- `error: string | null` — set if fetch or stream fails

On Generate: resets state, fires fetch, reads `response.body` as a `ReadableStream`, decodes with `TextDecoder`, appends each chunk to `response`.

## dataModel Handling

- The right column has a JSON textarea for the dataModel
- Client parses it with `JSON.parse` — invalid JSON shows an inline error, does not block Generate
- Parsed object is passed as the `dataModel` prop to `<Renderer>`
- The dataModel is **not** sent to the server — it is purely a client-side concern for the Renderer

## package.json Scripts

```json
{
  "scripts": {
    "dev": "concurrently \"tsx watch server/index.ts\" \"vite\"",
    "build": "vite build",
    "preview": "vite preview"
  }
}
```

## Out of Scope

- Conversation / chat history — single-shot only
- Auth or API key management UI — `OPENAI_API_KEY` is read from `.env` on the server
- Styling framework — minimal inline styles or plain CSS
- Production deployment configuration
