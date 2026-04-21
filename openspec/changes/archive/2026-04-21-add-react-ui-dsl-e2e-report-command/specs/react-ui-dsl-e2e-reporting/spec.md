## ADDED Requirements

### Requirement: Dedicated e2e report command
`packages/react-ui-dsl` SHALL provide a dedicated developer command that runs the existing e2e fixture suite and generates a browser-viewable report without changing the behavior of the default `test:e2e` command.

#### Scenario: Standard e2e verification remains report-free
- **WHEN** a contributor runs the default `test:e2e` command
- **THEN** the e2e fixtures execute as verification only
- **AND** no report directory is generated as part of that default command

#### Scenario: Dedicated report command generates a report run
- **WHEN** a contributor runs the dedicated e2e report command
- **THEN** the e2e fixtures execute with report collection enabled
- **AND** the command generates a timestamped report directory for that run

### Requirement: Report directory output
The report workflow SHALL write each run to a new timestamped directory under the `react-ui-dsl` e2e test tree and SHALL include a browser entry point plus a structured report payload.

#### Scenario: Timestamped report directory is created
- **WHEN** a dedicated report run completes
- **THEN** the system creates a new report directory named with the run timestamp
- **AND** the directory contains an `index.html` report entry point
- **AND** the directory contains a structured report data file for the collected fixture results

### Requirement: Fixture report contents
Each collected fixture result SHALL include enough metadata for contributors to inspect the rendered outcome and correlate it back to the originating test fixture.

#### Scenario: Report entry includes fixture context
- **WHEN** a fixture is included in the generated report
- **THEN** its report entry includes the component name, fixture id, prompt, expected description, DSL, and pass/fail status

#### Scenario: Report entry includes lightweight failure information
- **WHEN** a fixture fails during parse, render, or assertion
- **THEN** its report entry records that the fixture failed
- **AND** the report includes a lightweight failure reason when one is available

### Requirement: Browser-rendered preview
The generated report SHALL render each fixture preview in a browser context using the `react-ui-dsl` rendering path rather than only embedding static serialized HTML from the test process.

#### Scenario: Report shows rendered preview
- **WHEN** a contributor opens the generated report in a browser
- **THEN** each fixture entry displays a rendered preview driven by the fixture's DSL and data model

### Requirement: Best-effort report generation for failing runs
The dedicated report workflow SHALL attempt to write the report output even when one or more fixtures fail, while preserving the underlying test failure outcome.

#### Scenario: Failing fixtures still appear in the report
- **WHEN** one or more fixtures fail during a dedicated report run
- **THEN** the report is still generated if the report writer can complete
- **AND** the report includes the completed fixture entries and failure statuses gathered before the run ended

#### Scenario: Test command still fails on fixture failure
- **WHEN** a fixture fails during a dedicated report run
- **THEN** the underlying test run still reports failure to the caller
- **AND** report generation does not convert the failing test run into a passing one

### Requirement: Automatic report opening
The dedicated report workflow SHALL print the generated report location and SHALL attempt to open the generated report for the contributor after creation.

#### Scenario: Report path is printed for manual fallback
- **WHEN** the dedicated report run finishes writing output
- **THEN** the command prints the generated report path so the contributor can open it manually if needed

#### Scenario: Browser opening is best effort
- **WHEN** the dedicated report run finishes writing output
- **THEN** the workflow attempts to open the generated report automatically
- **AND** a failure to open the browser does not suppress the report output or change the test result
