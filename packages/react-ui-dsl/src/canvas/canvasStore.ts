export interface CanvasCardData {
  title?: string;
  children: unknown[];
  size?: { w?: number };
  cardId: string;
}

export interface PreviewCardData {
  title?: string;
  children: unknown[];
  cardId: string;
  url?: string;
  iframeId?: string;
  data?: Record<string, unknown>;
}

export interface PreviewTabData {
  tabId: string;
  title: string;
  cards: PreviewCardData[];
}

export interface CanvasStoreState {
  canvasCards: CanvasCardData[];
  previewTabs: PreviewTabData[];
  activeKey: string;
  enableMultiTab: boolean;
}

export interface CanvasStore {
  canvasCards: CanvasCardData[];
  previewTabs: PreviewTabData[];
  activeKey: string;
  hasData: boolean;
  enableMultiTab: boolean;

  addCanvasCard(card: Omit<CanvasCardData, "cardId">, cardId?: string): string;
  addPreviewCard(card: Omit<PreviewCardData, "cardId">, tabId?: string, type?: "replace" | "append"): string;
  removeCanvasCard(cardId: string): void;
  removePreviewCard(tabId: string, cardId: string): void;
  removePreviewTab(tabId: string): void;
  setActiveKey(key: string): void;
  setEnableMultiTab(enabled: boolean): void;
  clear(): void;

  subscribe(listener: () => void): () => void;
  getSnapshot(): CanvasStoreState;
}

let cardIdCounter = 0;
let tabIdCounter = 0;

function generateCardId(): string {
  cardIdCounter++;
  return `card-${cardIdCounter}`;
}

function generateTabId(): string {
  tabIdCounter++;
  return `preview-${tabIdCounter}`;
}

function fallbackActiveKey(state: CanvasStoreState): string {
  if (state.canvasCards.length > 0) return "canvas";
  if (state.previewTabs.length > 0) return state.previewTabs[0].tabId;
  return "canvas";
}

function createCanvasStoreInternal(): CanvasStore {
  const state: CanvasStoreState = {
    canvasCards: [],
    previewTabs: [],
    activeKey: "canvas",
    enableMultiTab: false,
  };

  const listeners: Set<() => void> = new Set();

  function notify() {
    listeners.forEach(listener => listener());
  }

  return {
    canvasCards: state.canvasCards,
    previewTabs: state.previewTabs,
    activeKey: state.activeKey,
    enableMultiTab: state.enableMultiTab,
    get hasData(): boolean {
      return state.canvasCards.length > 0 || state.previewTabs.length > 0;
    },

    addCanvasCard(card, cardId): string {
      if (!state.enableMultiTab) {
        if (state.previewTabs.length > 0) {
          state.previewTabs = [];
        }
      }

      const resolvedCardId = cardId ?? generateCardId();
      const existingIndex = cardId
        ? state.canvasCards.findIndex(c => c.cardId === cardId)
        : -1;

      if (existingIndex >= 0) {
        state.canvasCards[existingIndex] = { ...card, cardId: resolvedCardId };
      } else {
        state.canvasCards.push({ ...card, cardId: resolvedCardId });
      }
      state.activeKey = "canvas";
      notify();
      return resolvedCardId;
    },

    addPreviewCard(card, tabId, type = "append"): string {
      const resolvedCardId = card.cardId ?? generateCardId();
      const newCard: PreviewCardData = { ...card, cardId: resolvedCardId };

      if (!state.enableMultiTab) {
        state.canvasCards = [];
        state.previewTabs = [];
        const singleTabId = "preview-single";
        state.previewTabs.push({
          tabId: singleTabId,
          title: card.title ?? "Preview",
          cards: [newCard],
        });
        state.activeKey = singleTabId;
        notify();
        return resolvedCardId;
      }

      const existingTabIndex = tabId
        ? state.previewTabs.findIndex(t => t.tabId === tabId)
        : -1;

      if (existingTabIndex >= 0) {
        const tab = state.previewTabs[existingTabIndex];
        if (type === "replace" && card.cardId) {
          const existingCardIndex = tab.cards.findIndex(c => c.cardId === card.cardId);
          if (existingCardIndex >= 0) {
            tab.cards[existingCardIndex] = newCard;
          } else {
            tab.cards.push(newCard);
          }
          if (card.title) {
            tab.title = card.title;
          }
        } else {
          tab.cards.push(newCard);
        }
        state.activeKey = tabId!;
      } else {
        const resolvedTabId = tabId ?? generateTabId();
        state.previewTabs.push({
          tabId: resolvedTabId,
          title: card.title ?? "Preview",
          cards: [newCard],
        });
        state.activeKey = resolvedTabId;
      }
      notify();
      return resolvedCardId;
    },

    removeCanvasCard(cardId): void {
      state.canvasCards = state.canvasCards.filter(c => c.cardId !== cardId);
      if (state.activeKey === "canvas" && state.canvasCards.length === 0) {
        state.activeKey = fallbackActiveKey(state);
      }
      notify();
    },

    removePreviewCard(tabId, cardId): void {
      const tabIndex = state.previewTabs.findIndex(t => t.tabId === tabId);
      if (tabIndex >= 0) {
        state.previewTabs[tabIndex].cards = state.previewTabs[tabIndex].cards.filter(c => c.cardId !== cardId);
        if (state.previewTabs[tabIndex].cards.length === 0) {
          state.previewTabs.splice(tabIndex, 1);
          if (state.activeKey === tabId) {
            state.activeKey = fallbackActiveKey(state);
          }
        }
      }
      notify();
    },

    removePreviewTab(tabId): void {
      state.previewTabs = state.previewTabs.filter(t => t.tabId !== tabId);
      if (state.activeKey === tabId) {
        state.activeKey = fallbackActiveKey(state);
      }
      notify();
    },

    setActiveKey(key): void {
      state.activeKey = key;
      notify();
    },

    setEnableMultiTab(enabled): void {
      state.enableMultiTab = enabled;
      if (!enabled) {
        if (state.activeKey === "canvas" && state.previewTabs.length > 0) {
          state.previewTabs = [];
        } else if (state.activeKey !== "canvas" && state.canvasCards.length > 0) {
          state.canvasCards = [];
        }
      }
      notify();
    },

    clear(): void {
      state.canvasCards = [];
      state.previewTabs = [];
      state.activeKey = "canvas";
      notify();
    },

    subscribe(listener): () => void {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    getSnapshot(): CanvasStoreState {
      return {
        canvasCards: [...state.canvasCards],
        previewTabs: state.previewTabs.map(t => ({ ...t, cards: [...t.cards] })),
        activeKey: state.activeKey,
        enableMultiTab: state.enableMultiTab,
      };
    },
  };
}

export const canvasStore = createCanvasStoreInternal();
