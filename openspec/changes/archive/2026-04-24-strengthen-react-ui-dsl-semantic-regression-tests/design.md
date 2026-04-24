## Context

React UI DSL already has useful protection at the prompt, parser, and fixture levels, but the current chart coverage leaves a gap between "DSL parsed and mounted" and "the rendered chart is semantically correct." The recent `LineChart` failure passed parse checks and basic ECharts init checks while still rendering an empty chart because `Series(...)` DSL nodes were reaching chart views as wrapped element values instead of plain `{ category, values }` objects.

This change needs to improve both runtime correctness and the tests that guard it. The testing design should catch failures as early as possible, keep failures easy to localize, and avoid pushing all semantic protection into slow fixture-driven e2e tests.

## Goals / Non-Goals

**Goals:**
- Detect parse-success/runtime-semantic failures before they ship.
- Ensure multi-series chart views treat virtual `Series` DSL nodes equivalently to plain series objects.
- Add reusable chart-focused test coverage that validates chart option semantics, not just mount side effects.
- Keep failures well-localized so regressions can be identified as adapter, view, or fixture issues.

**Non-Goals:**
- Introduce screenshot or pixel-diff based chart testing.
- Redesign the chart DSL surface or replace current ECharts-based rendering.
- Expand semantic regression coverage to every component type in this change.
- Eliminate unrelated existing typecheck failures outside the chart regression scope.

## Decisions

### Decision: Use a three-layer regression strategy
The change will guard semantic failures at three layers:

1. Runtime adapter/helper tests for DSL element unwrapping.
2. Chart view tests for final ECharts option shape.
3. High-value fixture e2e tests for LLM-generated DSL semantics.

This is preferred over relying on fixture e2e alone because helper/view tests fail faster and localize root cause more clearly. It is preferred over only unit tests because fixture coverage is still needed to catch parse-success regressions in generated DSL.

### Decision: Centralize chart series unwrapping in shared chart utilities
Multi-series charts currently duplicate logic for turning `series` props into ECharts series arrays, and only scatter data had explicit element-node unwrapping. The change will centralize `Series` element normalization in shared chart utilities and have LineChart, BarChart, AreaChart, HorizontalBarChart, and RadarChart consume that normalization.

This is preferred over patching each chart ad hoc because it gives one contract for all multi-series charts and one place to test element-wrapper behavior.

### Decision: Upgrade fixture assertions from mount checks to semantic option checks
For high-value chart fixtures, e2e assertions will inspect the ECharts `setOption()` payload and verify that key semantics survive end to end:
- expected axis labels
- expected series count
- expected series names
- expected series data arrays

This is preferred over checking only `echarts.init()` because init/mount success does not prove visible data made it into the chart. It is preferred over full browser-visual testing because option assertions are much cheaper and more stable while still targeting the semantic failure mode.

### Decision: Keep prompt-surface tests, but treat them as complementary
Prompt guidance that discourages fabricated chart inputs remains valuable, but it is not sufficient protection for runtime-semantic regressions. Prompt tests will remain as a separate guardrail, while runtime and fixture tests carry the primary responsibility for catching empty-but-valid charts.

This is preferred over removing prompt tests because prompt regressions can still create bad DSL shapes before runtime code sees them.

## Risks / Trade-offs

- [Semantic assertions become too fixture-specific] → Keep broad reusable assertions in shared helpers and reserve detailed per-fixture option checks for a small number of high-value regressions.
- [Runtime normalization silently diverges across chart types] → Route all multi-series charts through the same shared `Series` normalization helper and test that helper directly.
- [E2E tests become slower or noisier] → Limit semantic option assertions to targeted chart fixtures instead of expanding every fixture immediately.
- [Developers misread prompt fixes as sufficient protection] → Document in the design/tasks that prompt tests are secondary and runtime/view contracts are the primary safety net for this failure class.

## Migration Plan

1. Add shared chart normalization helpers and focused runtime tests.
2. Update multi-series chart views to consume normalized series values.
3. Strengthen the targeted chart fixture to assert actual ECharts option semantics.
4. Keep the generated snapshot aligned and re-run the chart-focused fixture to confirm convergence.
5. Run chart unit tests and the React UI DSL e2e suite before implementation is considered complete.

Rollback is straightforward: revert the shared helper usage and the stricter fixture assertions together. Because this change tightens runtime correctness and tests without introducing storage or API migrations, no data migration is required.

## Open Questions

- Should future changes extend the same semantic-regression pattern to non-chart components such as `Descriptions`, `Tabs`, or `Form`?
- Should the e2e harness eventually expose higher-level reusable chart matchers so more fixtures can adopt semantic assertions with less duplication?
