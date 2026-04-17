## 1. Dependency and runtime setup

- [x] 1.1 Add `@openuidev/react-ui-dsl` and any required peer/runtime dependencies to `examples/react-ui-dsl-demo/package.json`
- [x] 1.2 Decide and implement the supported local resolution path for consuming `@openuidev/react-ui-dsl` from the demo client and server
- [x] 1.3 Add any required style imports or runtime setup needed by the real library in the demo app

## 2. Replace placeholder integration

- [x] 2.1 Replace the demo renderer import so `src/App.tsx` uses `dslLibrary` from `@openuidev/react-ui-dsl`
- [x] 2.2 Remove or retire `src/lib/placeholderLibrary.ts` from the runtime path once the real library is wired in
- [x] 2.3 Replace `server/systemPrompt.ts` so it derives the system prompt from `dslLibrary.prompt()`

## 3. Documentation and verification

- [x] 3.1 Update `examples/react-ui-dsl-demo/README.md` with the real-library setup, build prerequisites, and run instructions
- [x] 3.2 Verify the demo server can stream responses using the generated prompt without transport regressions
- [x] 3.3 Verify the demo client renders generated DSL with `@openuidev/react-ui-dsl` in the monorepo development flow
