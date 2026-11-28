# OpenUI rendering verification map

Use this index to cover the user-facing behavior affected by a change. Every feature uses the real workspace `Renderer`, `dslLibrary`, and Chromium helper described in [the verification skill](../SKILL.md).

## Baseline preconditions

- Start from the repository root.
- Supply real DSL and data from the issue, application request, or model run being verified.
- Keep each run's evidence in a separate `.audit/verify/` directory.
- Run the doctor before investigating an environment failure.

## Driving conventions

- Use `[data-case="<scenario-id>"]` for the scenario and `[data-render]` for the rendered content.
- Choose a DOM-index path from the evidence report for a layout target. Do not assume a component's CSS class or internal wrapper count.
- Check errors, screenshots, and geometry together.
- Record unobserved requirements as inconclusive.

## Feature entry contract

Each feature file describes user-observable behavior, entry points, the Playwright recipe, and traps. Do not encode implementation choices or fixed bug fixtures as feature requirements.

## Features

- [Content rendering](./content-rendering.md) covers text, Markdown, values, and data bindings.
- [Layout and responsive behavior](./layout.md) covers stack sizing, wrapping, overflow, and viewport changes.
- [Charts](./charts.md) covers visible series, labels, legends, and clipping.
- [Structured content](./structured-content.md) covers cards, tables, descriptions, forms, and tabs.
