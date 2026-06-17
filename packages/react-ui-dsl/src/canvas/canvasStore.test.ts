import { describe, expect, it, beforeEach } from "vitest";
import { canvasStore } from "./canvasStore";

describe("canvasStore", () => {
  beforeEach(() => {
    canvasStore.clear();
    canvasStore.setEnableMultiTab(true);
  });

  describe("addCanvasCard", () => {
    it("adds a card and returns cardId", () => {
      const cardId = canvasStore.addCanvasCard({ title: "Devices", children: [], size: { w: 6 } });

      expect(cardId).toMatch(/^card-/);
      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(1);
      expect(snapshot.canvasCards[0].title).toBe("Devices");
    });

    it("adds a card with custom cardId", () => {
      const cardId = canvasStore.addCanvasCard({ title: "Devices", children: [] }, "my-card");

      expect(cardId).toBe("my-card");
      expect(canvasStore.getSnapshot().canvasCards[0].cardId).toBe("my-card");
    });

    it("replaces card with same cardId", () => {
      canvasStore.addCanvasCard({ title: "Devices", children: [] }, "card-1");
      canvasStore.addCanvasCard({ title: "Devices Updated", children: [], size: { w: 12 } }, "card-1");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(1);
      expect(snapshot.canvasCards[0].title).toBe("Devices Updated");
      expect(snapshot.canvasCards[0].size?.w).toBe(12);
    });

    it("adds multiple cards with different cardIds", () => {
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addCanvasCard({ title: "Card B", children: [] });

      expect(canvasStore.getSnapshot().canvasCards).toHaveLength(2);
    });

    it("switches activeKey to canvas", () => {
      canvasStore.addCanvasCard({ title: "Card", children: [] });

      expect(canvasStore.getSnapshot().activeKey).toBe("canvas");
    });
  });

  describe("addPreviewCard", () => {
    it("adds a preview tab and switches activeKey", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [] });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(1);
      expect(snapshot.previewTabs[0].title).toBe("Device List");
      expect(snapshot.previewTabs[0].cards).toHaveLength(1);
      expect(snapshot.activeKey).toBe(snapshot.previewTabs[0].tabId);
    });

    it("always creates new tab when no tabId provided", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [] });
      canvasStore.addPreviewCard({ title: "Device List", children: [], url: "https://x.com", iframeId: "x" });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(2);
    });

    it("replaces card in existing tab when tabId exists and type=replace", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [{ typeName: "Table" }], cardId: "card-1" }, "device-tab");
      canvasStore.addPreviewCard({ title: "Device List", children: [{ typeName: "Chart" }], cardId: "card-1" }, "device-tab", "replace");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(1);
      expect(snapshot.previewTabs[0].tabId).toBe("device-tab");
      expect(snapshot.previewTabs[0].cards).toHaveLength(1);
      expect(snapshot.previewTabs[0].cards[0].cardId).toBe("card-1");
    });

    it("appends card to existing tab when tabId exists and type=append", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [{ typeName: "Table" }] }, "device-tab");
      canvasStore.addPreviewCard({ title: "Extra", children: [{ typeName: "Chart" }] }, "device-tab");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(1);
      expect(snapshot.previewTabs[0].cards).toHaveLength(2);
      expect(snapshot.previewTabs[0].title).toBe("Device List");
    });

    it("creates new tab when tabId provided but not found", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [] }, "device-tab");
      canvasStore.addPreviewCard({ title: "Network", children: [] }, "network-tab");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(2);
      expect(snapshot.previewTabs[1].tabId).toBe("network-tab");
    });

    it("uses provided tabId when creating new tab", () => {
      canvasStore.addPreviewCard({ title: "Device List", children: [] }, "my-custom-id");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs[0].tabId).toBe("my-custom-id");
    });

    it("adds multiple preview tabs with different titles", () => {
      canvasStore.addPreviewCard({ title: "Tab A", children: [] });
      canvasStore.addPreviewCard({ title: "Tab B", children: [] });

      expect(canvasStore.getSnapshot().previewTabs).toHaveLength(2);
    });
  });

  describe("removeCanvasCard", () => {
    it("removes a specific card", () => {
      const id1 = canvasStore.addCanvasCard({ title: "Card 1", children: [] });
      canvasStore.addCanvasCard({ title: "Card 2", children: [] });

      canvasStore.removeCanvasCard(id1);

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(1);
      expect(snapshot.canvasCards[0].title).toBe("Card 2");
    });

    it("is a no-op for unknown cardId", () => {
      canvasStore.addCanvasCard({ title: "Card 1", children: [] });
      canvasStore.removeCanvasCard("nonexistent");

      expect(canvasStore.getSnapshot().canvasCards).toHaveLength(1);
    });
  });

  describe("removePreviewCard", () => {
    it("removes a card from a preview tab", () => {
      const cardId = canvasStore.addPreviewCard({ title: "Tab", children: [] }, "tab-x");
      canvasStore.addPreviewCard({ title: "Tab", children: [] }, "tab-x");

      canvasStore.removePreviewCard("tab-x", cardId);

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs[0].cards).toHaveLength(1);
    });

    it("removes the tab when last card is removed", () => {
      const cardId = canvasStore.addPreviewCard({ title: "Tab", children: [] }, "tab-x");

      canvasStore.removePreviewCard("tab-x", cardId);

      expect(canvasStore.getSnapshot().previewTabs).toHaveLength(0);
    });
  });

  describe("removePreviewTab", () => {
    it("removes a preview tab by tabId", () => {
      canvasStore.addPreviewCard({ title: "Tab A", children: [] });
      const snapshot = canvasStore.getSnapshot();
      const tabId = snapshot.previewTabs[0].tabId;

      canvasStore.removePreviewTab(tabId);

      expect(canvasStore.getSnapshot().previewTabs).toHaveLength(0);
    });

    it("switches activeKey away from removed tab", () => {
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addPreviewCard({ title: "Preview", children: [] });
      const snapshot = canvasStore.getSnapshot();
      const previewTabId = snapshot.previewTabs[0].tabId;

      canvasStore.setActiveKey(previewTabId);
      canvasStore.removePreviewTab(previewTabId);

      expect(canvasStore.getSnapshot().activeKey).toBe("canvas");
    });
  });

  describe("clear", () => {
    it("removes all cards, tabs, and resets activeKey", () => {
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addPreviewCard({ title: "Preview", children: [] });

      canvasStore.clear();

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(0);
      expect(snapshot.previewTabs).toHaveLength(0);
      expect(snapshot.activeKey).toBe("canvas");
    });
  });

  describe("hasData", () => {
    it("returns false when empty", () => {
      expect(canvasStore.hasData).toBe(false);
    });

    it("returns true when canvas card exists", () => {
      canvasStore.addCanvasCard({ title: "Card", children: [] });
      expect(canvasStore.hasData).toBe(true);
    });

    it("returns true when preview tab exists", () => {
      canvasStore.addPreviewCard({ title: "Preview", children: [] });
      expect(canvasStore.hasData).toBe(true);
    });
  });

  describe("subscribe", () => {
    it("notifies listeners on state changes", () => {
      const calls: number[] = [];
      canvasStore.subscribe(() => calls.push(1));

      canvasStore.addCanvasCard({ title: "Card", children: [] });
      canvasStore.clear();

      expect(calls.length).toBeGreaterThanOrEqual(2);
    });

    it("unsubscribe stops notifications", () => {
      const calls: number[] = [];
      const unsub = canvasStore.subscribe(() => calls.push(1));

      unsub();
      canvasStore.addCanvasCard({ title: "Card", children: [] });

      expect(calls).toHaveLength(0);
    });
  });
});

describe("colSpanFromWidth", () => {
  function colSpanFromWidth(w: number | undefined, totalCols: number): number {
    if (!w) return 1;
    return Math.max(1, Math.min(totalCols, Math.round((w / 12) * totalCols)));
  }

  it("returns 1 for undefined width (default)", () => {
    expect(colSpanFromWidth(undefined, 2)).toBe(1);
  });

  it("maps w=6 to 1 column in 2-column layout", () => {
    expect(colSpanFromWidth(6, 2)).toBe(1);
  });

  it("maps w=12 to full columns (2) in 2-column layout", () => {
    expect(colSpanFromWidth(12, 2)).toBe(2);
  });

  it("maps w=6 to 2 columns in 4-column layout", () => {
    expect(colSpanFromWidth(6, 4)).toBe(2);
  });

  it("maps w=3 to 1 column in 4-column layout", () => {
    expect(colSpanFromWidth(3, 4)).toBe(1);
  });

  it("clamps span to total columns when w > 12", () => {
    expect(colSpanFromWidth(24, 2)).toBe(2);
  });

  it("clamps span to minimum 1 when w is 0", () => {
    expect(colSpanFromWidth(0, 2)).toBe(1);
  });
});

describe("masonry layout algorithm", () => {
  const GAP = 16;
  const EST_HEIGHT = 300;

  function computeMasonryLayout(
    cards: { cardId: string; size?: { w?: number } }[],
    columns: number,
    gap: number,
    estimatedHeight: number
  ) {
    function colSpanFromWidth(w: number | undefined, totalCols: number): number {
      if (!w) return 1;
      return Math.max(1, Math.min(totalCols, Math.round((w / 12) * totalCols)));
    }

    const colHeights = new Array(columns).fill(0);
    const layouts: { cardId: string; colSpan: number; top: number; height: number; colIndex: number }[] = [];

    for (const card of cards) {
      const span = colSpanFromWidth(card.size?.w, columns);
      const height = estimatedHeight;

      let bestCol = 0;
      let bestTop = colHeights[0];
      for (let c = 0; c < columns; c++) {
        if (c + span > columns) continue;
        const top = colHeights[c];
        if (top < bestTop) {
          bestCol = c;
          bestTop = top;
        }
      }

      const top = bestTop;
      for (let c = bestCol; c < bestCol + span && c < columns; c++) {
        colHeights[c] = top + height + gap;
      }

      layouts.push({ cardId: card.cardId, colSpan: span, top, height, colIndex: bestCol });
    }

    return { layouts, colHeights };
  }

  it("places single half-width card in column 0", () => {
    const result = computeMasonryLayout(
      [{ cardId: "c1", size: { w: 6 } }],
      2, GAP, EST_HEIGHT
    );

    expect(result.layouts[0].colIndex).toBe(0);
    expect(result.layouts[0].colSpan).toBe(1);
    expect(result.layouts[0].top).toBe(0);
  });

  it("places single full-width card spanning both columns", () => {
    const result = computeMasonryLayout(
      [{ cardId: "c1", size: { w: 12 } }],
      2, GAP, EST_HEIGHT
    );

    expect(result.layouts[0].colSpan).toBe(2);
    expect(result.layouts[0].colIndex).toBe(0);
    expect(result.layouts[0].top).toBe(0);
  });

  it("stacks half-width cards in masonry (shortest column first)", () => {
    const result = computeMasonryLayout(
      [
        { cardId: "c1", size: { w: 6 } },
        { cardId: "c2", size: { w: 6 } },
        { cardId: "c3", size: { w: 6 } },
      ],
      2, GAP, EST_HEIGHT
    );

    expect(result.layouts[0].colIndex).toBe(0);
    expect(result.layouts[1].colIndex).toBe(1);
    expect(result.layouts[2].colIndex).toBe(0);
    expect(result.layouts[2].top).toBe(EST_HEIGHT + GAP);
  });

  it("full-width card advances both column heights", () => {
    const result = computeMasonryLayout(
      [
        { cardId: "c1", size: { w: 12 } },
        { cardId: "c2", size: { w: 6 } },
      ],
      2, GAP, EST_HEIGHT
    );

    expect(result.layouts[0].colSpan).toBe(2);
    expect(result.layouts[1].colIndex).toBe(0);
    expect(result.layouts[1].top).toBe(EST_HEIGHT + GAP);
  });

  it("computes correct total content height", () => {
    const result = computeMasonryLayout(
      [
        { cardId: "c1", size: { w: 6 } },
        { cardId: "c2", size: { w: 6 } },
        { cardId: "c3", size: { w: 6 } },
      ],
      2, GAP, EST_HEIGHT
    );

    const maxBottom = Math.max(...result.layouts.map(l => l.top + l.height));
    expect(maxBottom).toBe(EST_HEIGHT + GAP + EST_HEIGHT);
  });

  it("handles 4-column layout correctly", () => {
    const result = computeMasonryLayout(
      [
        { cardId: "c1", size: { w: 6 } },
        { cardId: "c2", size: { w: 3 } },
        { cardId: "c3", size: { w: 3 } },
        { cardId: "c4", size: { w: 6 } },
      ],
      4, GAP, EST_HEIGHT
    );

    expect(result.layouts[0].colSpan).toBe(2);
    expect(result.layouts[0].colIndex).toBe(0);
    expect(result.layouts[1].colSpan).toBe(1);
    expect(result.layouts[1].colIndex).toBe(2);
    expect(result.layouts[2].colSpan).toBe(1);
    expect(result.layouts[2].colIndex).toBe(3);
    expect(result.layouts[3].colSpan).toBe(2);
    expect(result.layouts[3].colIndex).toBe(0);
  });
});

describe("virtual scroll binary search", () => {
  function findVisibleRange(
    layouts: { top: number; height: number }[],
    scrollTop: number,
    viewportHeight: number,
    buffer: number
  ) {
    if (layouts.length === 0) return { start: 0, end: 0 };

    const viewTop = scrollTop - buffer;
    const viewBottom = scrollTop + viewportHeight + buffer;

    let lo = 0;
    let hi = layouts.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (layouts[mid].top + layouts[mid].height < viewTop) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    const start = lo;

    lo = start;
    hi = layouts.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (layouts[mid].top <= viewBottom) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    const end = hi;

    return { start, end };
  }

  const layouts: { top: number; height: number }[] = [
    { top: 0, height: 300 },
    { top: 0, height: 250 },
    { top: 316, height: 300 },
    { top: 266, height: 200 },
    { top: 616, height: 300 },
    { top: 482, height: 250 },
    { top: 932, height: 300 },
    { top: 766, height: 200 },
    { top: 1232, height: 300 },
    { top: 1098, height: 250 },
  ];

  it("returns all items when viewport covers entire content", () => {
    const range = findVisibleRange(layouts, 0, 2000, 300);
    expect(range.start).toBe(0);
    expect(range.end).toBe(layouts.length - 1);
  });

  it("returns visible subset for mid-page scroll", () => {
    const range = findVisibleRange(layouts, 400, 400, 300);
    expect(range.start).toBeLessThanOrEqual(2);
    expect(range.end).toBeGreaterThanOrEqual(4);
  });

  it("returns empty range for empty layouts", () => {
    const range = findVisibleRange([], 0, 800, 300);
    expect(range.start).toBe(0);
    expect(range.end).toBe(0);
  });

  it("handles scrolled to bottom", () => {
    const range = findVisibleRange(layouts, 1100, 400, 300);
    expect(range.start).toBeGreaterThanOrEqual(4);
    expect(range.end).toBe(layouts.length - 1);
  });

  it("includes buffer zone items", () => {
    const range = findVisibleRange(layouts, 500, 200, 100);
    expect(range.start).toBeLessThanOrEqual(2);
    expect(range.end).toBeGreaterThanOrEqual(4);
  });
});

describe("single-view mode (enableMultiTab=false)", () => {
  beforeEach(() => {
    canvasStore.clear();
    canvasStore.setEnableMultiTab(false);
  });

  describe("addCanvasCard", () => {
    it("appends canvasCard when existing content is canvasCards", () => {
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addCanvasCard({ title: "Card B", children: [] });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(2);
      expect(snapshot.activeKey).toBe("canvas");
    });

    it("clears previewTabs and shows canvasCard when existing content is previewCard", () => {
      canvasStore.addPreviewCard({ title: "Preview A", children: [] });
      canvasStore.addCanvasCard({ title: "Card A", children: [] });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(0);
      expect(snapshot.canvasCards).toHaveLength(1);
      expect(snapshot.activeKey).toBe("canvas");
    });

    it("replaces existing previewCard with canvasCard", () => {
      canvasStore.addPreviewCard({ title: "Preview", children: [] });
      expect(canvasStore.getSnapshot().previewTabs).toHaveLength(1);

      canvasStore.addCanvasCard({ title: "Dashboard", children: [] });
      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(0);
      expect(snapshot.canvasCards).toHaveLength(1);
    });
  });

  describe("addPreviewCard", () => {
    it("creates single preview tab and clears canvasCards", () => {
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addCanvasCard({ title: "Card B", children: [] });
      canvasStore.addPreviewCard({ title: "Preview", children: [] });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(0);
      expect(snapshot.previewTabs).toHaveLength(1);
      expect(snapshot.previewTabs[0].tabId).toBe("preview-single");
      expect(snapshot.activeKey).toBe("preview-single");
    });

    it("replaces previous previewCard with new one", () => {
      canvasStore.addPreviewCard({ title: "First", children: [] });
      canvasStore.addPreviewCard({ title: "Second", children: [] });

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs).toHaveLength(1);
      expect(snapshot.previewTabs[0].title).toBe("Second");
      expect(snapshot.previewTabs[0].cards).toHaveLength(1);
    });

    it("ignores tabId and type parameters in single-view mode", () => {
      canvasStore.addPreviewCard({ title: "Preview", children: [] }, "custom-tab", "replace");

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.previewTabs[0].tabId).toBe("preview-single");
      expect(snapshot.previewTabs[0].title).toBe("Preview");
    });
  });

  describe("setEnableMultiTab", () => {
    it("defaults to false", () => {
      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.enableMultiTab).toBe(false);
    });

    it("can be toggled to true", () => {
      canvasStore.setEnableMultiTab(true);
      expect(canvasStore.getSnapshot().enableMultiTab).toBe(true);
    });

    it("preserves enableMultiTab through clear()", () => {
      canvasStore.setEnableMultiTab(true);
      canvasStore.addCanvasCard({ title: "Card", children: [] });
      canvasStore.clear();

      expect(canvasStore.getSnapshot().enableMultiTab).toBe(true);
    });

    it("clears conflicting content when switching to single-view", () => {
      canvasStore.setEnableMultiTab(true);
      canvasStore.addCanvasCard({ title: "Card A", children: [] });
      canvasStore.addPreviewCard({ title: "Preview A", children: [] });

      canvasStore.setEnableMultiTab(false);

      const snapshot = canvasStore.getSnapshot();
      expect(snapshot.canvasCards).toHaveLength(0);
      expect(snapshot.previewTabs).toHaveLength(1);
    });
  });
});
