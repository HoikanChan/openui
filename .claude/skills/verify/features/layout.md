# Layout and responsive behavior

Layout behavior keeps content within its container, uses the intended space, and places columns on the expected rows.

## Sub-features

- `layout-columns` places siblings on one row when the DSL requests it.
- `layout-width` uses available container width without unused space.
- `layout-overflow` avoids horizontal page overflow.
- `layout-viewports` preserves behavior at the reported viewport and container widths.

## How to get to it (user POV)

- Open a host view containing the reported layout.
- Resize the host container or browser viewport when the issue depends on width.

## Driving it with Playwright

Preconditions:

- The scenario preserves the real DSL, data, and width.
- The layout target is identified by a unique path and optional text.

- **Discover paths.** Run once with text assertions. Read `report.json` for every node path and rectangle.
- **Check wrapping.** Add `sameRow` targets. Children on different rows fail.
- **Check width.** Add `childrenFillWidth` targets. Unused width beyond `tolerance` fails.
- **Check overflow.** Leave `noHorizontalOverflow` enabled. Inspect the screenshot for visible clipping.
- **Repeat sizes.** Add scenarios for each reported width and compare their measurements.

## Gotchas

- Do not test layout by matching CSS source declarations.
- A correct rectangle can still contain clipped text. Inspect the image.
- `childrenFillWidth` applies to children that are meant to fill the parent. Do not apply it to intentionally content-sized rows.
- Missing and ambiguous layout targets fail instead of passing silently.
