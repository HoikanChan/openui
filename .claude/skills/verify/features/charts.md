# Charts

Charts display their series, labels, and legends at the requested size without cutting off meaningful content.

## Sub-features

- `chart-series` draws the supplied data.
- `chart-labels` keeps axis and category information readable.
- `chart-responsive` adapts at narrow widths.

## How to get to it (user POV)

- Open a host view containing the supplied chart DSL.
- Resize the view to the reported width.

## Driving it with Playwright

Preconditions:

- The scenario contains real chart labels, series, and data.
- Allow enough `readyDelayMs` for the chart to render.

- **Render.** Run the helper and inspect the scenario screenshot. The chart and its category information are visible.
- **Check errors.** Leave parser, runtime, and browser error allowances disabled. A blank canvas with no error needs visual investigation.
- **Check overflow.** Enable `noClipping` for CSS overflow evidence and leave page overflow enabled.
- **Check narrow output.** Add a scenario with the narrow width. Confirm hidden outer labels remain available in the legend or tooltip.

## Gotchas

- Canvas pixels are not available through DOM text assertions. Read the screenshot.
- `noClipping` measures CSS boxes. It cannot prove that canvas-rendered text is intact.
- Do not infer chart correctness from the existence of a canvas element.
