## ADDED Requirements

### Requirement: Library shall support immutable extension
Front-end `Library` SHALL provide an extension API that returns a new Library containing base components plus extension components while leaving the original Library unchanged.

#### Scenario: Extending library returns new instance
- **WHEN** a caller invokes `dslLibrary.extend(...)` with component `BizCard`
- **THEN** the returned Library contains `BizCard`
- **AND** the original `dslLibrary` does not contain `BizCard`

#### Scenario: Extended library preserves base behavior
- **WHEN** a caller uses an extended Library
- **THEN** base components such as `Stack` remain available for rendering and prompt generation
- **AND** the extended Library can still call `prompt()`, `toSpec()`, and `toJSONSchema()`

### Requirement: Library extension shall reject component name collisions
Front-end Library extension SHALL reject component names that already exist in the base Library or repeat inside the extension payload.

#### Scenario: Extension component collides with base component
- **WHEN** a caller extends `dslLibrary` with a component named `Stack`
- **THEN** extension fails with a component name collision error

#### Scenario: Extension payload repeats component name
- **WHEN** an extension payload contains two components named `BizCard`
- **THEN** extension fails with a component name collision error

### Requirement: Library shall export generation contract
Front-end Library SHALL export a model-visible generation contract that can be consumed by Java Generation SDK without renderer implementations. The export MAY reuse the existing `toSpec()`/CLI JSON output path as long as the generated artifact contains the required contract fields.

#### Scenario: Base library exports contract
- **WHEN** a caller exports a generation contract from the base DSLEngine Library
- **THEN** the contract contains component prompt specs and component groups
- **AND** the contract does not contain React component implementations

#### Scenario: Extended library exports extension-aware contract
- **WHEN** a caller exports a generation contract from an extended Library
- **THEN** the contract includes extension component prompt specs
- **AND** the contract includes extension component groups

#### Scenario: Contract includes version
- **WHEN** a generation contract is exported
- **THEN** the contract includes a contract version value

### Requirement: Front-end build shall generate DSLEngine base contract artifact
The front-end build workflow SHALL generate a DSLEngine base contract JSON artifact from the official base Library so Java Generation SDK can package it as its default base contract.

#### Scenario: Build produces base contract JSON
- **WHEN** the base contract generation script runs
- **THEN** it writes a JSON artifact containing the DSLEngine base generation contract
- **AND** the artifact includes the base contract version

#### Scenario: Java SDK can package generated artifact
- **WHEN** the Java SDK build runs after the base contract artifact exists
- **THEN** the Java SDK can include the artifact as a resource
- **AND** SDK callers are not required to pass the base contract manually on the primary initialization path

### Requirement: Base contract artifact shall include the builtins manifest
The DSLEngine base contract artifact SHALL include an ordered builtins manifest so a downstream consumer can render builtin prompt documentation identically to the front-end without executing builtins.

#### Scenario: Manifest lists builtin docs in prompt order
- **WHEN** the base contract artifact is generated
- **THEN** the artifact includes an ordered `builtins` array
- **AND** each entry contains `signature`, `description`, and `templateBuiltin`
- **AND** the order matches the front-end prompt assembly order, with eager builtins before lazy builtins

#### Scenario: Manifest omits runtime implementations
- **WHEN** the builtins manifest is exported
- **THEN** entries do not include builtin runtime functions
- **AND** the manifest does not distinguish eager from lazy builtins

### Requirement: Component groups shall reference known components
Front-end Library extension and generation contract export SHALL validate that every component listed in a component group exists in the resulting Library.

#### Scenario: Extension group references extension component
- **WHEN** an extension adds component `BizCard`
- **AND** its component group references `BizCard`
- **THEN** extension succeeds

#### Scenario: Extension group references missing component
- **WHEN** an extension component group references `MissingCard`
- **THEN** extension fails with a validation error that identifies `MissingCard`
