# Product Overview

## Product Purpose

`@openuidev/lang-core` is the framework-agnostic core library of the OpenUI Lang ecosystem. It enables LLMs to generate structured, interactive UI declaratively via a custom DSL (OpenUI Lang). The library handles the full pipeline: parsing LLM-generated text into a typed element tree, generating system prompts from component specs, evaluating reactive expressions at runtime, and merging incremental LLM edits into existing programs.

## Target Users

1. **Framework adapter authors** — building `@openuidev/react-lang`, `@openuidev/vue-lang`, `@openuidev/svelte-lang`. They import `lang-core` for the parser, runtime, and type definitions, then add framework-specific rendering on top.
2. **App developers** building LLM-powered generative UI products who want direct access to the parsing/runtime pipeline without a framework adapter.

This package has no React or other framework dependencies — it is a pure TypeScript library.

## Key Features

1. **Parser** — Converts OpenUI Lang text into a typed `ElementNode` tree. Supports one-shot (`createParser`) and streaming (`createStreamingParser`) modes for real-time LLM output.
2. **Prompt Generation** — `generatePrompt(spec)` produces a complete LLM system prompt from a component spec, tool definitions, and feature flags (toolCalls, bindings, editMode, inlineMode).
3. **Runtime Evaluator** — Evaluates reactive expressions, `$variable` refs, and Query/Mutation results at runtime. Includes a reactive store (`createStore`) and query manager (`createQueryManager`).
4. **Incremental Merge** — `mergeStatements(original, patch)` merges LLM-generated patches into an existing program, preserving unchanged statements.
5. **Library Definition API** — `defineComponent` / `createLibrary` for registering Zod-schematized components that the parser and prompt generator consume.
6. **MCP Integration** — Optional `@modelcontextprotocol/sdk` integration for tool call execution via `createQueryManager` and `extractToolResult`.

## Product Principles

1. **Framework-agnostic**: No React, Vue, Svelte, or DOM dependencies. All framework-specific code lives in adapter packages.
2. **Zero runtime surprises**: Parsing is deterministic. Streaming mode produces stable, progressively-resolved results on each chunk.
3. **LLM-first error design**: `OpenUIError` and `ValidationError` types are structured for LLM correction loops — machine-readable codes, actionable hints, and statement-level targeting.
4. **Dual-format distribution**: Ships both ESM and CJS builds with full TypeScript declarations, ensuring compatibility across bundlers and Node.js environments.
