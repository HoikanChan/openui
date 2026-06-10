import { describe, expect, it } from "vitest";
import { z } from "zod";
import { createLibrary, defineComponent } from "../library";

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
    expect(contract.components.Stack.signature).toContain("Stack(");
    expect(contract.components.BizCard.signature).toContain("BizCard(");
    expect(contract.componentGroups?.map((group) => group.name)).toEqual(["Layout", "Business"]);
    expect(contract.additionalRules).toEqual(["Prefer business cards"]);
    expect(Object.keys(contract.components.BizCard).sort()).toEqual(["description", "signature"]);
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
