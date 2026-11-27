import { describe, expect, test } from "vitest";
import meta, { Loading } from "./index.stories";

describe("Tabs story", () => {
  test("Default story has two loaded tabs in args", () => {
    expect(meta.args.items).toHaveLength(2);
    expect(meta.args.items[0].loading).toBe(false);
    expect(meta.args.items[1].loading).toBe(false);
  });

  test("Loading story has one loading tab", () => {
    expect(Loading.args?.items?.[1].loading).toBe(true);
  });
});
