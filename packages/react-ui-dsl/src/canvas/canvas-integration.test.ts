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
      expect(result.data.tab).toBe("Dashboard");
      expect(result.data.size).toEqual({ w: 6 });
    }
  });

  it("accepts custom tab and size", () => {
    const result = CanvasCardSchema.safeParse({
      children: [],
      title: "Devices",
      tab: "Network",
      size: { w: 12 },
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.title).toBe("Devices");
      expect(result.data.tab).toBe("Network");
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

  it("CanvasCard pushes children to canvasStore as DashboardCard", () => {
    const children = [{ typeName: "Table", props: { columns: [], data: "data" } }];
    canvasStore.addDashboardCard({
      title: "Devices",
      children,
      size: { w: 6 },
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.tabs["Dashboard"]).toHaveLength(1);
    expect(snapshot.tabs["Dashboard"][0].title).toBe("Devices");
    expect(snapshot.tabs["Dashboard"][0].children).toEqual(children);
    expect(snapshot.tabs["Dashboard"][0].size?.w).toBe(6);
  });

  it("CanvasCard with same title replaces existing card", () => {
    canvasStore.addDashboardCard({
      title: "Devices",
      children: [{ typeName: "Table" }],
    });
    canvasStore.addDashboardCard({
      title: "Devices",
      children: [{ typeName: "Chart" }],
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.tabs["Dashboard"]).toHaveLength(1);
  });

  it("CanvasCard with custom tab creates that tab", () => {
    canvasStore.addDashboardCard({
      title: "Network Devices",
      children: [],
      size: { w: 12 },
    }, "Network");

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.tabs["Network"]).toHaveLength(1);
  });
});

describe("PreviewCard + canvasStore integration", () => {
  beforeEach(() => {
    canvasStore.clear();
  });

  it("PreviewCard pushes children to canvasStore as PreviewTab", () => {
    const children = [{ typeName: "Table", props: {} }];
    canvasStore.addPreviewTab({
      title: "Device List",
      children,
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].title).toBe("Device List");
    expect(snapshot.previewTabs[0].children).toEqual(children);
  });

  it("PreviewCard with HTMLLoader children adds url/iframeId/data to tab", () => {
    canvasStore.addPreviewTab({
      title: "External Page",
      children: [],
      url: "https://example.com",
      iframeId: "iframe-1",
      data: { theme: "dark" },
    });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs[0].url).toBe("https://example.com");
    expect(snapshot.previewTabs[0].iframeId).toBe("iframe-1");
    expect(snapshot.previewTabs[0].data).toEqual({ theme: "dark" });
  });

  it("PreviewCard with same title replaces existing preview tab", () => {
    canvasStore.addPreviewTab({ title: "X", children: [] });
    canvasStore.addPreviewTab({ title: "X", children: [], url: "https://new.com", iframeId: "new" });

    const snapshot = canvasStore.getSnapshot();
    expect(snapshot.previewTabs).toHaveLength(1);
    expect(snapshot.previewTabs[0].url).toBe("https://new.com");
  });
});