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

describe("Renderer locale integration", () => {
  it("passes the renderer locale into @Format* evaluation by default", () => {
    const createdAt = "2026-01-02T03:04:05.000Z";
    const html = renderToStaticMarkup(
      <Renderer
        response={
          'root = Root([Label(@FormatNumber(data.amount, 2)), Label(@FormatPercent(data.ratio, 1)), Label(@FormatDate(data.createdAt, "date"))])'
        }
        library={library}
        locale="de-DE"
        dataModel={{ amount: 12345.67, ratio: 0.125, createdAt }}
      />,
    );

    expect(html).toContain(
      new Intl.NumberFormat("de-DE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(12345.67),
    );
    expect(html).toContain(
      new Intl.NumberFormat("de-DE", {
        style: "percent",
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(0.125),
    );
    expect(html).toContain(
      new Intl.DateTimeFormat("de-DE", { dateStyle: "medium" }).format(new Date(createdAt)),
    );
  });

  it("allows explicit locale arguments to override the renderer locale", () => {
    const html = renderToStaticMarkup(
      <Renderer
        response={'root = Root([Label(@FormatNumber(data.amount, 2, "en-US"))])'}
        library={library}
        locale="de-DE"
        dataModel={{ amount: 12345.67 }}
      />,
    );

    expect(html).toContain(
      new Intl.NumberFormat("en-US", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(12345.67),
    );
    expect(html).not.toContain(
      new Intl.NumberFormat("de-DE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(12345.67),
    );
  });
});
