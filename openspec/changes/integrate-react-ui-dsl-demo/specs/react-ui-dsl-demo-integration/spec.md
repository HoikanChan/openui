## ADDED Requirements

### Requirement: Demo renders with the real React UI DSL library
The `examples/react-ui-dsl-demo` application SHALL use `dslLibrary` from `@openuidev/react-ui-dsl` as the renderer library instead of maintaining a local placeholder component registry.

#### Scenario: Client renderer uses workspace package
- **WHEN** the demo application starts in the monorepo
- **THEN** the preview renderer imports `dslLibrary` from `@openuidev/react-ui-dsl`
- **AND** the demo does not depend on `src/lib/placeholderLibrary.ts` for runtime rendering

### Requirement: Demo server generates prompts from the same library contract
The demo server SHALL build its system prompt from the `dslLibrary` exported by `@openuidev/react-ui-dsl` so prompt generation and client rendering use the same DSL definition.

#### Scenario: Server prompt source matches renderer library
- **WHEN** the server prepares the system prompt for `/api/generate`
- **THEN** it derives the prompt from `dslLibrary.prompt()`
- **AND** it does not rely on a handwritten DSL schema string that must be updated separately

### Requirement: Demo setup documents real-library prerequisites
The demo documentation SHALL describe the dependency, build, and runtime prerequisites required to run the example against `@openuidev/react-ui-dsl` inside the workspace.

#### Scenario: Contributor follows documented setup
- **WHEN** a contributor reads `examples/react-ui-dsl-demo/README.md`
- **THEN** the README lists the required workspace dependency and any build or peer dependency prerequisites for `@openuidev/react-ui-dsl`
- **AND** the instructions describe how to start the demo with the real library rather than the placeholder path
