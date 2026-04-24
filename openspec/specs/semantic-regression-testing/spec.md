## ADDED Requirements

### Requirement: Regression fixtures SHALL detect parse-success runtime-semantic failures
The React UI DSL regression suite SHALL fail fixtures that parse successfully and mount their runtime container but lose meaningful rendered semantics by the time the final runtime payload is built.

#### Scenario: Chart fixture asserts final option semantics
- **WHEN** a chart fixture renders DSL that reaches ECharts successfully
- **THEN** the fixture assertion layer MUST be able to inspect the final `setOption()` payload
- **AND** the fixture MUST be able to assert axis labels, series names, and series data values instead of only checking that `echarts.init()` was called

#### Scenario: Empty semantic chart output fails even when mount succeeds
- **WHEN** a chart fixture parses successfully, calls `echarts.init()`, and mounts a chart container
- **BUT** the emitted chart option has missing or undefined series names or data arrays
- **THEN** the fixture MUST fail as a semantic regression

### Requirement: Runtime contracts SHALL be testable independently of fixture e2e
The React UI DSL chart regression suite SHALL provide focused runtime-level tests for DSL element unwrapping and chart option construction so semantic failures can be localized before full fixture execution.

#### Scenario: DSL Series nodes are verified at the helper layer
- **WHEN** a runtime helper receives a virtual `Series` DSL node represented as an element wrapper
- **THEN** unit tests MUST verify that the helper normalizes it into plain `{ category, values }` data before chart views consume it

#### Scenario: Chart view tests verify data-bearing series output
- **WHEN** a multi-series chart view receives normalized labels and series data
- **THEN** unit or integration tests MUST verify that the generated ECharts option contains data-bearing series entries with the expected names and values
