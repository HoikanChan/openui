import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import { Renderer } from "../Renderer";
import { createLibrary, defineComponent } from "../library";

const Root = defineComponent({
  name: "Root",
  description: "Root container",
  props: z.object({
    children: z.array(z.any()),
  }),
  component: ({ props, renderNode }) => <div>{props.children.map((child) => renderNode(child))}</div>,
});

const Label = defineComponent({
  name: "Label",
  description: "Simple label",
  props: z.object({
    text: z.any(),
  }),
  component: ({ props }) => <span>{String(props.text)}</span>,
});

const library = createLibrary({
  root: "Root",
  components: [Root, Label],
});

describe("Renderer dataModel integration", () => {
  it("renders nested object access: data.user.name", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.user.name)])'}
        library={library}
        dataModel={{ user: { name: "Alice" } }}
      />,
    );

    expect(html).toContain("Alice");
  });

  it("renders null when dataModel is omitted and data.* is referenced", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.x)])'}
        library={library}
        // no dataModel prop
      />,
    );

    // data.x is unresolved — rendered as "null" but no crash
    expect(html).toContain("null");
  });

  it("renders a top-level scalar: data.count", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.count)])'}
        library={library}
        dataModel={{ count: 42 }}
      />,
    );

    expect(html).toContain("42");
  });

  it("renders three levels of nesting: data.org.team.name", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.org.team.name)])'}
        library={library}
        dataModel={{ org: { team: { name: "Platform" } } }}
      />,
    );

    expect(html).toContain("Platform");
  });

  it("renders a different dataModel on a separate call (dataModel update)", () => {
    const html1 = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.user.name)])'}
        library={library}
        dataModel={{ user: { name: "Alice" } }}
      />,
    );
    const html2 = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.user.name)])'}
        library={library}
        dataModel={{ user: { name: "Bob" } }}
      />,
    );

    expect(html1).toContain("Alice");
    expect(html2).toContain("Bob");
  });

  it("accesses a specific array element by index: data.items[0].label", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.items[0].label)])'}
        library={library}
        dataModel={{ items: [{ label: "First" }, { label: "Second" }] }}
      />,
    );

    expect(html).toContain("First");
    expect(html).not.toContain("Second");
  });

  it("iterates an array with @Each: data.items via loop variable", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([@Each(data.items, item, Label(item.label))])'}
        library={library}
        dataModel={{ items: [{ label: "Apple" }, { label: "Banana" }, { label: "Cherry" }] }}
      />,
    );

    expect(html).toContain("Apple");
    expect(html).toContain("Banana");
    expect(html).toContain("Cherry");
  });
});
