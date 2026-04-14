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
  it("renders host data through the data root", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(data.user.name)])'}
        library={library}
        dataModel={{ user: { name: "Alice" } }}
      />,
    );

    expect(html).toContain("Alice");
  });
});
