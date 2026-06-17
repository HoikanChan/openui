import { describe, expect, it, beforeEach } from "vitest";
import { CanvasCardSchema } from "./CanvasCard/schema";
import { PreviewCardSchema } from "./PreviewCard/schema";
import { HTMLLoaderSchema } from "./HTMLLoader/schema";
import { canvasStore } from "./canvasStore";

describe("CanvasCard schema", () => {
  it("accepts valid props with defaults", () => {
    const result = CanvasCardSchema.safeParse({
      children: [],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.size).toEqual({ w: 6 });
    }
  });

  it("accepts custom size", () => {
    const result = CanvasCardSchema.safeParse({
      children: [],
      title: "Devices",
      size: { w: 12 },
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.title).toBe("Devices");
      expect(result.data.size?.w).toBe(12);
    }
  });

  it("rejects unknown props", () => {
    expect(CanvasCardSchema.safeParse({ children: [], foo: "bar" }).success).toBe(false);
  });

  it("rejects size.w outside 1-12 range", () => {
    expect(CanvasCardSchema.safeParse({ children: [], size: { w: 0 } }).success).toBe(false);
    expect(CanvasCardSchema.safeParse({ children: [], size: { w: 13 } }).success).toBe(false);
  });

  it("rejects tab prop (no longer supported)", () => {
    expect(CanvasCardSchema.safeParse({ children: [], tab: "Network" }).success).toBe(false);
  });

  it("accepts optional title", () => {
    expect(CanvasCardSchema.safeParse({ children: [] }).success).toBe(true);
    expect(CanvasCardSchema.safeParse({ children: [], title: "Card" }).success).toBe(true);
  });
});

describe("PreviewCard schema", () => {
  it("accepts valid props", () => {
    const result = PreviewCardSchema.safeParse({
      children: [],
      title: "Device List",
    });
    expect(result.success).toBe(true);
  });

  it("requires title", () => {
    expect(PreviewCardSchema.safeParse({ children: [] }).success).toBe(false);
  });

  it("accepts optional summary", () => {
    const result = PreviewCardSchema.safeParse({
      children: [],
      title: "Preview",
      summary: [{ typeName: "TextContent", props: { text: "Summary" } }],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.summary).toBeDefined();
    }
  });

  it("accepts optional type", () => {
    const result = PreviewCardSchema.safeParse({
      children: [],
      title: "Preview",
      type: "replace",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.type).toBe("replace");
    }
  });

  it("accepts optional tabId", () => {
    const result = PreviewCardSchema.safeParse({
      children: [],
      title: "Preview",
      tabId: "my-tab",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.tabId).toBe("my-tab");
    }
  });

  it("accepts optional cardId", () => {
    const result = PreviewCardSchema.safeParse({
      children: [],
      title: "Preview",
      cardId: "my-card",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.cardId).toBe("my-card");
    }
  });

  it("rejects unknown props", () => {
    expect(PreviewCardSchema.safeParse({ children: [], title: "X", url: "bad" }).success).toBe(false);
  });
});

describe("HTMLLoader schema", () => {
  it("accepts valid props with data", () => {
    const result = HTMLLoaderSchema.safeParse({
      url: "https://example.com",
      iframeId: "iframe-1",
      data: {},
    });
    expect(result.success).toBe(true);
  });

  it("requires data field (not optional)", () => {
    expect(HTMLLoaderSchema.safeParse({
      url: "https://example.com",
      iframeId: "iframe-1",
    }).success).toBe(false);
  });

  it("requires url and iframeId", () => {
    expect(HTMLLoaderSchema.safeParse({ url: "https://example.com" }).success).toBe(false);
    expect(HTMLLoaderSchema.safeParse({ iframeId: "iframe-1" }).success).toBe(false);
  });

  it("accepts optional data", () => {
    const result = HTMLLoaderSchema.safeParse({
      url: "https://example.com",
      iframeId: "iframe-1",
      data: { key: "value" },
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.data).toEqual({ key: "value" });
    }
  });

  it("rejects unknown props", () => {
    expect(HTMLLoaderSchema.safeParse({ url: "x", iframeId: "y", children: [] }).success).toBe(false);
  });
});

describe("CanvasCard + canvasStore integration", () => {
  beforeEach(() => {
    canvasStore.clear();
  });

  it("CanvasCard pushes children to canvasStore", () => {
    const children = [{ typeName: "Table", props: { columns: [], data: "data" } }];
    canvasStore.addCanvasCard({
      title: "Devices",
      children,
      size: { w: 6 },
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.canvasCards).toHaveLength(1);
    expect(snapshot.canvasCards[0].title).toBe("Devices");
    expect(snapshot.canvasCards[0].children).toEqual(children);
    expect(snapshot.canvasCards[0].size?.w).toBe(6);
  });

  it("CanvasCard with same cardId replaces existing card", () => {
    canvasStore.addCanvasCard({
      title: "Devices",
      children: [{ typeName: "Table" }],
    }, "card-1");
    canvasStore.addCanvasCard({
      title: "Devices Updated",
      children: [{ typeName: "Chart" }],
    }, "card-1");

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.canvasCards).toHaveLength(1);
    expect(snapshot.canvasCards[0].title).toBe("Devices Updated");
  });

  it("CanvasCard with different cardId adds as new card", () => {
    canvasStore.addCanvasCard({ title: "Card A", children: [] });
    canvasStore.addCanvasCard({ title: "Card B", children: [] });

    expect(canvasStore.getSnapshot().canvasCards).toHaveLength(2);
  });
});

describe("PreviewCard + canvasStore integration", () => {
  beforeEach(() => {
    canvasStore.clear();
    canvasStore.setEnableMultiTab(true);
  });

  it("PreviewCard pushes children to canvasStore as PreviewTab", () => {
    const children = [{ typeName: "Table", props: {} }];
    canvasStore.addPreviewCard({
      title: "Device List",
      children,
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].title).toBe("Device List");
    expect(snapshot.previewTabs[0].cards[0].children).toEqual(children);
  });

  it("PreviewCard with HTMLLoader children adds url/iframeId/data", () => {
    canvasStore.addPreviewCard({
      title: "External Page",
      children: [],
      url: "https://example.com",
      iframeId: "iframe-1",
      data: { theme: "dark" },
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs[0].cards[0].url).toBe("https://example.com");
    expect(snapshot.previewTabs[0].cards[0].iframeId).toBe("iframe-1");
    expect(snapshot.previewTabs[0].cards[0].data).toEqual({ theme: "dark" });
  });

  it("PreviewCard with tabId replaces card when type=replace", () => {
    canvasStore.addPreviewCard({ title: "X", children: [], cardId: "c1" }, "tab-x");
    canvasStore.addPreviewCard({ title: "X", children: [], url: "https://new.com", iframeId: "new", cardId: "c1" }, "tab-x", "replace");

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].cards).toHaveLength(1);
    expect(snapshot.previewTabs[0].cards[0].url).toBe("https://new.com");
  });

  it("PreviewCard with tabId appends card when type=append", () => {
    canvasStore.addPreviewCard({ title: "X", children: [{ typeName: "Table" }] }, "tab-x");
    canvasStore.addPreviewCard({ title: "Extra", children: [{ typeName: "Chart" }] }, "tab-x");

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].cards).toHaveLength(2);
  });

  it("PreviewCard with unknown tabId creates new tab", () => {
    canvasStore.addPreviewCard({ title: "A", children: [] }, "tab-a");
    canvasStore.addPreviewCard({ title: "B", children: [] }, "tab-b");

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(2);
    expect(snapshot.previewTabs[1].tabId).toBe("tab-b");
  });

  it("PreviewCard without tabId always creates new tab", () => {
    canvasStore.addPreviewCard({ title: "X", children: [] });
    canvasStore.addPreviewCard({ title: "X", children: [] });

    expect(canvasStore.getSnapshot().previewTabs).toHaveLength(2);
  });
});

describe("PreviewCard + canvasStore integration (single-view mode)", () => {
  beforeEach(() => {
    canvasStore.clear();
    canvasStore.setEnableMultiTab(false);
  });

  it("PreviewCard clears canvasCards and creates single preview tab", () => {
    canvasStore.addCanvasCard({ title: "Card", children: [] });
    canvasStore.addPreviewCard({ title: "Preview", children: [] });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.canvasCards).toHaveLength(0);
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].tabId).toBe("preview-single");
  });

  it("PreviewCard replaces previous preview in single-view mode", () => {
    canvasStore.addPreviewCard({ title: "First", children: [] });
    canvasStore.addPreviewCard({ title: "Second", children: [] });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].title).toBe("Second");
  });

  it("CanvasCard clears previewTabs in single-view mode", () => {
    canvasStore.addPreviewCard({ title: "Preview", children: [] });
    canvasStore.addCanvasCard({ title: "Card", children: [] });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(0);
    expect(snapshot.canvasCards).toHaveLength(1);
  });
});
