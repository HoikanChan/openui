## Why

`examples/react-ui-dsl-demo` still runs on a placeholder DSL library and a hardcoded system prompt, so it does not validate the real `@openuidev/react-ui-dsl` package end to end. Now that the package exports `dslLibrary`, the demo should exercise the actual library surface so the example stays aligned with the published package and exposes integration issues earlier.

## What Changes

- Replace the demo's placeholder client library with a workspace dependency on `@openuidev/react-ui-dsl`.
- Replace the server's handwritten prompt string with a prompt generated from the real `dslLibrary`.
- Define the build and runtime requirements needed for the demo to import `@openuidev/react-ui-dsl` from the monorepo during local development.
- Update demo documentation so setup instructions match the real package integration path.

## Capabilities

### New Capabilities
- `react-ui-dsl-demo-integration`: The demo consumes the real `@openuidev/react-ui-dsl` library for both rendering and prompt generation, with documented setup requirements for local development.

### Modified Capabilities

None.

## Impact

- Affected code: `examples/react-ui-dsl-demo`, `packages/react-ui-dsl`, and supporting workspace configuration or docs needed for local consumption.
- Dependencies: adds a workspace dependency from the demo to `@openuidev/react-ui-dsl` and brings along its peer dependency expectations in the demo environment.
- Systems: local development flow, server prompt generation, and example documentation for the React UI DSL demo.
