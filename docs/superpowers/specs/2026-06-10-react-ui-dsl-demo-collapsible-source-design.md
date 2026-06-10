# React UI DSL Demo Collapsible Source Panel

## Goal

Update the left side of `examples/react-ui-dsl-demo` so the Open Lang source
panel is collapsed by default and Preview receives all remaining space.

## Behavior

- The source tab bar remains visible when the source panel is collapsed.
- A control on the right side of the source tab bar toggles the panel.
- The initial state is collapsed.
- Expanding restores the current vertical split: source content and Preview
  each consume half of the available left-column height.
- Collapsing hides only the source content. Preview grows to fill the remaining
  left-column height and continues to use the full width of its parent.
- The Prompt and Data Model sidebar keeps its existing width and behavior.
- Switching between Open Lang, Parsed JSON, and Prompt remains available while
  collapsed; selecting a tab does not automatically expand the panel.

## Implementation

Add a local boolean state to `App` for the source panel's collapsed state.
Keep the existing tab bar mounted, conditionally render the source content,
and change the source panel's flex sizing based on that state. Add an
accessible toggle button with an updated label and expanded state.

No persistence is required: each page load starts with the source panel
collapsed.

## Testing

Add focused component tests that verify:

- the source content is hidden and Preview is available on initial render;
- activating the toggle exposes the Open Lang editor;
- activating it again hides the source content;
- existing source tab behavior remains intact after expansion.

