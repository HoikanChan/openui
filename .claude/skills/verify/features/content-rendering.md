# Content rendering

Content rendering displays values and formatted expressions as text instead of blank output or an error boundary.

## Sub-features

- `text-values` renders strings, numbers, and supported data values.
- `text-bindings` evaluates data references and built-in expressions.
- `markdown-content` renders formatted content through the real component.

## How to get to it (user POV)

- Open a host view that renders the supplied DSL.
- Request a view whose data contains the affected value or expression.

## Driving it with Playwright

Preconditions:

- The scenario contains the original DSL and data.
- The affected content is inside the scenario's `[data-render]` element.

- **Render.** Run `verify.mjs` with the scenario and a new evidence directory. The browser displays the requested content.
- **Check text.** Set `expectText` to required text and `absentText` to text that must disappear. The report records the rendered text.
- **Check errors.** Leave parser and runtime error allowances disabled. Unexpected errors fail.
- **Inspect.** Read the scenario screenshot. It shows the value at the reported size.

## Gotchas

- A successful parser result does not prove that the runtime rendered text.
- An empty nested error array is not an error.
- A minimal example can prove the helper works, but cannot replace the reported input as bug evidence.
