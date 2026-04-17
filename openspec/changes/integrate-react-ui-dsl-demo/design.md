## Context

`examples/react-ui-dsl-demo` currently proves the rendering loop with a local placeholder library in `src/lib/placeholderLibrary.ts` and a handwritten prompt string in `server/systemPrompt.ts`. That keeps the demo runnable before `@openuidev/react-ui-dsl` is wired in, but it also means the example is no longer validating the real package contract exported from `packages/react-ui-dsl/src/index.ts`.

The integration touches multiple boundaries:

- The Vite client must render with the real `dslLibrary`.
- The Express server must generate its system prompt from the same library so prompt generation and rendering stay in sync.
- The demo package must satisfy the real library's dependency and build expectations, including Ant Design, ECharts, and any required CSS imports.
- Local development instructions must describe how to run the demo when the library is consumed from the monorepo rather than a published package.

## Goals / Non-Goals

**Goals:**

- Make the demo import `dslLibrary` from `@openuidev/react-ui-dsl` instead of a local placeholder implementation.
- Make the server derive the system prompt from the same library used by the renderer.
- Define a repeatable local development path for workspace consumption, including dependency, alias, and build prerequisites.
- Keep the demo as the canonical integration example for the package.

**Non-Goals:**

- Redesign the demo UI or request/response streaming flow.
- Add new DSL components or change `@openuidev/react-ui-dsl` component semantics.
- Publish the package externally or solve general package distribution concerns outside the monorepo.

## Decisions

### Consume the real library from the demo package

The demo will depend on `@openuidev/react-ui-dsl` through a workspace dependency and import `dslLibrary` directly on both client and server. This removes duplicated component registration and prevents the prompt schema from drifting away from the renderer.

Alternative considered: keep the placeholder library and only align it manually.
Rejected because the placeholder already lags the real package surface and duplicates prompt behavior in two files.

### Use one library source for both rendering and prompt generation

`server/systemPrompt.ts` should call `dslLibrary.prompt()` instead of maintaining a handwritten prompt. The generated prompt is the authoritative contract for the renderer, so using the library directly makes schema and prompt updates land in one place.

Alternative considered: pre-generate a checked-in prompt file.
Rejected because the demo is intended as a development example inside the monorepo, and a generated file still introduces a second artifact that can go stale.

### Make workspace integration explicit in tooling and docs

The demo currently relies on Vite aliases for unbuilt source imports and documents only `react-lang` and `react-ui` builds. The integration work should explicitly define whether `react-ui-dsl` is consumed from source, from its built output, or via additional aliases for both Vite and the server entrypoint. The chosen path must be documented alongside required peer dependencies and CSS setup.

Alternative considered: rely on implicit hoisting and ad hoc local fixes.
Rejected because server and client load modules differently, and undocumented assumptions will make the example fragile for contributors.

## Risks / Trade-offs

- [Server-side module resolution differs from Vite resolution] -> Document and implement a single supported strategy for `server/systemPrompt.ts`, such as importing built output or adding an equivalent server-safe resolution path.
- [Real library peers increase demo setup complexity] -> Add the required workspace dependency and document any additional peer packages or styles the demo must load.
- [The package may still require a prior build step] -> Keep build prerequisites explicit in the README and avoid claiming zero-build startup if the library cannot support it.
- [Prompt output may change once generated from the real library] -> Treat the real library prompt as source of truth and verify the demo still accepts streamed responses without changing the transport contract.

## Migration Plan

1. Add the real package dependency and any missing peers to `examples/react-ui-dsl-demo`.
2. Replace placeholder imports in the client and server with imports from `@openuidev/react-ui-dsl`.
3. Update bundler or server resolution as needed so both environments can load the same library entrypoint.
4. Update the demo README with the final setup, build, and run instructions.
5. Verify the demo can stream a generated response and render it with the real library.

Rollback is straightforward: restore the placeholder library import path and hardcoded prompt while leaving the package work intact.

## Open Questions

- Should the demo import `@openuidev/react-ui-dsl` from built `dist` output only, or is direct source consumption a supported monorepo pattern for this package?
- Does the demo need to import Ant Design or ECharts styles explicitly once the real library is used?
- Is Node version support for the demo expected to move upward to match the package build tooling, or should the integration preserve the current lightweight server workflow?
