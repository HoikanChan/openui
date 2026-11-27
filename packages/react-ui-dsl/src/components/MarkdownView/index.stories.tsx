import type { Meta, StoryObj } from "@storybook/react";
import Markdown from "./index";

const meta = {
  title: "Components/MarkdownView",
  component: Markdown,
  args: {
    md: "# Hello",
  },
} satisfies Meta<typeof Markdown>;

export default meta;
type Story = StoryObj<typeof meta>;

const richMd = `# MarkdownView Smoke Test

A paragraph with **bold**, *italic*, ~~strike~~, and \`inline code\`.
A second line via GFM line break.

## Lists

- one
- two
  - nested
- three

1. first
2. second
3. third

## Links

A normal markdown link: [OpenUI](https://example.com "tooltip").

A bare URL autolink should render as text: https://example.com

An email autolink should also render as text: user@example.com

## Blockquote

> Quoted text — should be styled by github-markdown-css.

## Code block (highlight.js)

\`\`\`ts
interface User {
  id: number;
  name: string;
}

function greet(u: User): string {
  return \`hello, \${u.name}\`;
}
\`\`\`

## Table

| Name | Type    | Default |
| ---- | ------- | ------- |
| md   | string  | —       |
| style| object  | —       |

## Image

![alt text](https://picsum.photos/seed/openui/200/100)

---

Horizontal rule above.
`;

export const Rich: Story = {
  args: {
    md: richMd,
  },
};

export const Simple: Story = {
  args: {
    md: "# Hello\n\nThis is **MarkdownView**.",
  },
};

export const CodeBlock: Story = {
  args: {
    md: "```js\nconst x = 1;\nconsole.log(x);\n```",
  },
};

export const Empty: Story = {
  args: {
    md: "",
  },
};
