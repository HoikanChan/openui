// Minimal placeholder library — renders basic DSL types using plain HTML/inline styles.
// No dependency on @openuidev/react-ui (which requires a build step unavailable on Node <20.12).
//
// TODO: replace with the real library once @openuidev/react-ui-dsl is published:
//   import { dslLibrary } from "@openuidev/react-ui-dsl";
//   export { dslLibrary };

import { createLibrary, defineComponent } from "@openuidev/react-lang";
import React from "react";
import { z } from "zod";

const styleSchema = z.record(z.string(), z.any()).optional();

const VLayout = defineComponent({
  name: "VLayout",
  props: z.object({ properties: z.object({ gap: z.number().optional() }).optional(), style: styleSchema }),
  description: "Vertical layout container",
  component: ({ props, children }) =>
    React.createElement("div", {
      style: { display: "flex", flexDirection: "column", gap: props.properties?.gap ?? 8, ...props.style },
    }, children),
});

const HLayout = defineComponent({
  name: "HLayout",
  props: z.object({ properties: z.object({ gap: z.number().optional(), wrap: z.boolean().optional() }).optional(), style: styleSchema }),
  description: "Horizontal layout container",
  component: ({ props, children }) =>
    React.createElement("div", {
      style: { display: "flex", flexDirection: "row", flexWrap: props.properties?.wrap ? "wrap" : "nowrap", gap: props.properties?.gap ?? 8, ...props.style },
    }, children),
});

const Text = defineComponent({
  name: "Text",
  props: z.object({ properties: z.object({ type: z.enum(["default", "markdown", "html"]).optional(), content: z.string() }), style: styleSchema }),
  description: "Text content",
  component: ({ props }) => {
    const { type = "default", content } = props.properties;
    if (type === "html") return React.createElement("div", { style: props.style, dangerouslySetInnerHTML: { __html: content } });
    return React.createElement("p", { style: { margin: 0, ...props.style } }, content);
  },
});

const Button = defineComponent({
  name: "Button",
  props: z.object({ properties: z.object({ text: z.string().optional(), status: z.enum(["default", "primary", "risk"]).optional(), disabled: z.boolean().optional() }).optional(), style: styleSchema }),
  description: "Clickable button",
  component: ({ props }) => {
    const { text = "Button", status = "default", disabled = false } = props.properties ?? {};
    const bg = status === "primary" ? "#0070f3" : status === "risk" ? "#e53e3e" : "#eee";
    const color = status === "default" ? "#333" : "#fff";
    return React.createElement("button", { disabled, style: { padding: "8px 16px", borderRadius: 4, border: "none", background: bg, color, cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.6 : 1, ...props.style } }, text);
  },
});

const Card = defineComponent({
  name: "Card",
  props: z.object({ properties: z.object({ header: z.string().optional(), headerAlign: z.enum(["left", "center", "right"]).optional() }).optional(), style: styleSchema }),
  description: "Card container with optional header",
  component: ({ props, children }) =>
    React.createElement("div", { style: { border: "1px solid #e2e8f0", borderRadius: 8, overflow: "hidden", ...props.style } },
      props.properties?.header && React.createElement("div", { style: { padding: "12px 16px", borderBottom: "1px solid #e2e8f0", fontWeight: 600, textAlign: props.properties.headerAlign ?? "left" } }, props.properties.header),
      React.createElement("div", { style: { padding: 16 } }, children),
    ),
});

const ListItem = defineComponent({
  name: "ListItem",
  props: z.object({ style: styleSchema }),
  description: "List item",
  component: ({ children }) => React.createElement("li", null, children),
});

const List = defineComponent({
  name: "List",
  props: z.object({ properties: z.object({ header: z.string().optional(), isOrder: z.boolean().optional() }).optional(), style: styleSchema }),
  description: "Ordered or unordered list",
  component: ({ props, children }) => {
    const tag = props.properties?.isOrder ? "ol" : "ul";
    return React.createElement("div", { style: props.style },
      props.properties?.header && React.createElement("div", { style: { fontWeight: 600, marginBottom: 4 } }, props.properties.header),
      React.createElement(tag, { style: { paddingLeft: 20, margin: 0 } }, children),
    );
  },
});

const Select = defineComponent({
  name: "Select",
  props: z.object({ properties: z.object({ options: z.array(z.object({ label: z.string(), value: z.union([z.string(), z.number()]) })), defaultValue: z.union([z.string(), z.number()]).optional(), allowClear: z.boolean().optional() }), style: styleSchema }),
  description: "Dropdown select",
  component: ({ props }) =>
    React.createElement("select", { defaultValue: props.properties.defaultValue, style: { padding: "6px 8px", borderRadius: 4, border: "1px solid #ccc", ...props.style } },
      props.properties.options.map((o) => React.createElement("option", { key: String(o.value), value: o.value }, o.label)),
    ),
});

const Table = defineComponent({
  name: "Table",
  props: z.object({ properties: z.object({ columns: z.array(z.object({ title: z.string(), field: z.string(), sortable: z.boolean().optional() })) }), style: styleSchema }),
  description: "Data table",
  component: ({ props }) =>
    React.createElement("table", { style: { width: "100%", borderCollapse: "collapse", ...props.style } },
      React.createElement("thead", null,
        React.createElement("tr", null,
          props.properties.columns.map((c) => React.createElement("th", { key: c.field, style: { padding: "8px 12px", borderBottom: "2px solid #e2e8f0", textAlign: "left", fontWeight: 600 } }, c.title)),
        ),
      ),
    ),
});

const Link = defineComponent({
  name: "Link",
  props: z.object({ properties: z.object({ href: z.string(), text: z.string().optional(), target: z.enum(["_self", "_blank"]).optional(), disabled: z.boolean().optional() }), style: styleSchema }),
  description: "Anchor link",
  component: ({ props }) =>
    React.createElement("a", { href: props.properties.href, target: props.properties.target ?? "_self", style: { color: "#0070f3", textDecoration: "underline", ...props.style } }, props.properties.text ?? props.properties.href),
});

export const dslLibrary = createLibrary({
  root: "VLayout",
  components: [VLayout, HLayout, Text, Button, Card, List, ListItem, Select, Table, Link],
});
