# Structured content

Structured components display grouped content, records, forms, and tabs using the supplied DSL and data.

## Sub-features

- `structured-cards` displays titled groups and nested content.
- `structured-tables` displays rows and cell content.
- `structured-descriptions` displays field labels and values.
- `structured-forms` displays labels, inputs, and available actions.
- `structured-tabs` displays the selected panel and available tab labels.

## How to get to it (user POV)

- Open the host view containing the supplied structured component.
- Select a tab or edit a form when that interaction is under test.

## Driving it with Playwright

Preconditions:

- The scenario contains the original component DSL and data.
- The required values are known from the input, not invented for the assertion.

- **Render.** Run the helper with expected labels and values in `expectText`.
- **Inspect.** Read the scenario screenshot for layout and visible state.
- **Interact when needed.** Use Playwright against `[data-case]` with accessible roles and names. Record the action and resulting state in a companion script and the evidence directory.
- **Check errors.** Require no unexpected parser, runtime, or browser errors.

## Gotchas

- The generic helper covers initial rendering. Add explicit browser actions for tabs, input, submission, or other interactions.
- Third-party library warnings remain in the report. Allow only exact, reviewed messages.
- Do not use internal setters or component calls as a substitute for the real renderer.
