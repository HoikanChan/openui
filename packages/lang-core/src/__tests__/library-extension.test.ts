import { describe, expect, it } from "vitest";
import { z } from "zod";
import { createLibrary, defineComponent, generateComponentSpecs } from "../library";

const NullRenderer = () => null;

function component(name: string) {
  return defineComponent({
    name,
    props: z.object({
      title: z.string(),
    }),
    description: `${name} description`,
    component: NullRenderer,
  });
}

describe("Library extension contract", () => {
  it("extends immutably and exports renderer-free contract data", () => {
    const Stack = component("Stack");
    const BizCard = component("BizCard");
    const library = createLibrary({
      root: "Stack",
      contractVersion: "base-v1",
      components: [Stack],
      componentGroups: [{ name: "Layout", components: ["Stack"] }],
    });

    const extended = library.extend({
      contractVersion: "biz-v1",
      components: [BizCard],
      componentGroups: [{ name: "Business", components: ["BizCard"] }],
      additionalRules: ["Prefer business cards"],
    });

    expect(extended).not.toBe(library);
    expect(library.components.BizCard).toBeUndefined();
    expect(extended.components.BizCard).toBe(BizCard);

    const contract = extended.toSpec();
    expect(contract.contractVersion).toBe("biz-v1");
    expect(contract.components.Stack.propsSchema.properties.title.type).toBe("string");
    expect(contract.components.BizCard.propsSchema.properties.title.type).toBe("string");
    expect(contract.componentGroups?.map((group) => group.name)).toEqual(["Layout", "Business"]);
    expect(contract.additionalRules).toEqual(["Prefer business cards"]);
    expect(Object.keys(contract.components.BizCard).sort()).toEqual(["description", "propsSchema"]);
  });

  it("generates controlled propsSchema with stable positional property order", () => {
    const Leaf = defineComponent({
      name: "Leaf",
      props: z.object({
        label: z.string(),
        tone: z.enum(["info", "warning"]).optional(),
      }),
      description: "Leaf description",
      component: NullRenderer,
    });
    const Panel = defineComponent({
      name: "Panel",
      props: z.object({
        title: z.string(),
        tags: z.array(z.string()),
        child: Leaf.ref,
        options: z.object({
          compact: z.boolean(),
          count: z.number().optional(),
        }),
        children: z.array(Leaf.ref).optional(),
      }),
      description: "Panel description",
      component: NullRenderer,
    });

    const specs = generateComponentSpecs([Leaf, Panel]);

    expect(Object.keys(specs.Panel.propsSchema.properties)).toEqual([
      "title",
      "tags",
      "child",
      "options",
      "children",
    ]);
    expect(specs.Panel.propsSchema.required).toEqual(["title", "tags", "child", "options"]);
    expect(specs.Leaf.propsSchema.properties.tone.enum).toEqual(["info", "warning"]);
    expect(specs.Panel.propsSchema.properties.tags).toEqual({
      type: "array",
      items: { type: "string" },
    });
    expect(specs.Panel.propsSchema.properties.child).toEqual({ component: "Leaf" });
    expect(specs.Panel.propsSchema.properties.children).toEqual({
      type: "array",
      items: { component: "Leaf" },
    });
    expect(specs.Panel.propsSchema.properties.options).toEqual({
      type: "object",
      properties: {
        compact: { type: "boolean" },
        count: { type: "number" },
      },
      required: ["compact"],
    });
  });

  it("rejects schemas where required positional props follow optional props", () => {
    const Broken = defineComponent({
      name: "Broken",
      props: z.object({
        optionalTitle: z.string().optional(),
        requiredBody: z.string(),
      }),
      description: "Broken description",
      component: NullRenderer,
    });

    expect(() => generateComponentSpecs([Broken])).toThrow(
      /Broken.*requiredBody.*required props must be declared before optional props/,
    );
  });

  it("rejects component name collisions with the base library", () => {
    const Stack = component("Stack");
    const library = createLibrary({
      root: "Stack",
      components: [Stack],
    });

    expect(() => library.extend({ components: [component("Stack")] })).toThrow(
      /Component name collision: Stack/,
    );
  });

  it("rejects duplicate component names inside an extension payload", () => {
    const library = createLibrary({
      root: "Stack",
      components: [component("Stack")],
    });

    expect(() =>
      library.extend({
        components: [component("BizCard"), component("BizCard")],
      }),
    ).toThrow(/Component name collision: BizCard/);
  });

  it("rejects component groups that reference missing components", () => {
    const library = createLibrary({
      root: "Stack",
      components: [component("Stack")],
    });

    expect(() =>
      library.extend({
        componentGroups: [{ name: "Broken", components: ["MissingCard"] }],
      }),
    ).toThrow(/MissingCard/);
  });
});
