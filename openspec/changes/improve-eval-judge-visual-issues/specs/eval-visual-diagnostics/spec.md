## ADDED Requirements

### Requirement: Eval judge SHALL emit structured visual issue diagnostics
The eval judge SHALL return a `visual_issues` field for each judged fixture. The field SHALL be an array of zero or more normalized issue tags derived from the rendered screenshot, limited to the supported set: `overlap`, `wrong-direction`, `crowded`, `whitespace-imbalance`, `clipped`, and `weak-hierarchy`.

#### Scenario: Judge reports overlap and clipping as normalized tags
- **WHEN** a rendered fixture screenshot shows visible text or component overlap and clipped content
- **THEN** the judge output includes `visual_issues`
- **AND** the array contains `overlap` and `clipped`
- **AND** the judge does not invent tags outside the supported set

#### Scenario: Judge emits an empty diagnostics array for visually clean output
- **WHEN** a rendered fixture screenshot is visually readable and does not exhibit any supported diagnostics issues
- **THEN** the judge output includes `visual_issues`
- **AND** the field is an empty array

### Requirement: Visual readability defects SHALL constrain layout scoring
The eval judge SHALL treat visible screenshot readability defects as scoring inputs for `layout_coherence`. Any obvious overlap, clipping, or unreadable crowding MUST prevent a perfect layout score, and severe cases MUST reduce `layout_coherence` to a failing range.

#### Scenario: Severe overlap lowers layout coherence
- **WHEN** a rendered fixture screenshot contains obvious value or label overlap that makes the UI hard to read
- **THEN** the judge assigns `layout_coherence` below `3`
- **AND** the judge includes `overlap` in `visual_issues`

#### Scenario: Direction mismatch lowers layout coherence when scanability degrades
- **WHEN** a fixture renders key summary cards in a direction that materially harms scanning compared with the rendered content structure
- **THEN** the judge lowers `layout_coherence`
- **AND** the judge includes `wrong-direction` in `visual_issues`

#### Scenario: Severe crowding reduces overall score
- **WHEN** chart labels, legends, or metric values are crowded enough that the rendered output is difficult to interpret
- **THEN** the judge lowers `layout_coherence`
- **AND** the judge lowers `overall`
- **AND** the judge includes `crowded` in `visual_issues`

### Requirement: Eval outputs SHALL aggregate and display visual issue categories
The eval loop SHALL preserve `visual_issues` in per-fixture outputs and SHALL use the field to derive aggregate failing patterns for task bundles and reports.

#### Scenario: Task bundle preserves per-fixture visual issues
- **WHEN** a fixture judge result includes one or more `visual_issues`
- **THEN** the generated `targets.json` entry for that fixture includes the same normalized issue tags
- **AND** report/task-bundle consumers can display them without parsing free-form feedback

#### Scenario: Failing patterns group repeated visual issues
- **WHEN** multiple judged fixtures contain the same normalized visual issue tag
- **THEN** failing-pattern aggregation emits an issue-specific pattern for that tag
- **AND** the affected fixtures list contains the fixtures carrying that issue

### Requirement: Eval consumers SHALL remain compatible with historical judge results
Eval-loop readers and presenters SHALL continue to operate when persisted judge results do not contain `visual_issues`.

#### Scenario: Historical run data omits visual issue diagnostics
- **WHEN** an existing run artifact is loaded and one or more `JudgeScore` entries omit `visual_issues`
- **THEN** the eval loop treats the missing field as an empty diagnostics array
- **AND** report generation, task-bundle generation, and failing-pattern aggregation continue without error
