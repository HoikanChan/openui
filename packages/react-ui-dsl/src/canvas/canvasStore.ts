import { LibraryExtensionDefinition } from '@openuidev/lang-core';

export interface CanvasCardData {
  title?: string;
  children: unknown[];
  size?: { w?: number };
  cardId: string;
}

export interface PreviewCardData {
  title?: string;
  children: unknown[];
  cardId?: string;
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
  locale: string;
  theme: string;
  conversationId: string;
  extensions: GenUIExtension[],
}

export interface BaseState {
  locale?: string;
  theme?: string;
  conversationId?: string;
}

export type Tool = (args: Record<string, unknown>) => Promise<unknown>;
export interface GenUIExtension extends LibraryExtensionDefinition {
  extensionId?: string;
  toolProvider?: Record<string, Tool>;
}

export interface CanvasStore {
  canvasCards: CanvasCardData[];
  previewTabs: PreviewTabData[];
  activeKey: string;
  hasData: boolean;
  enableMultiTab: boolean;
  locale: string;
  theme: string;
  conversationId: string;
  extensions: GenUIExtension[];

  addExtension(ex: GenUIExtension): void;
  addCanvasCard(card: CanvasCardData, cardId?: string): string;
  addPreviewCard(card: PreviewCardData, tabId?: string, type?: "replace" | "append"): string;
  removeCanvasCard(cardId: string): void;
  removeCanvasTab(): void;
  removePreviewCard(tabId: string, cardId: string): void;
  removePreviewTab(tabId: string): void;
  setActiveKey(key: string): void;
  setEnableMultiTab(enabled: boolean): void;
  setBaseStore(state: BaseState): void;
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

function cloneExtensionRegistration(
  extension: GenUIExtension,
): GenUIExtension {
  return {
    extensionId: extension.extensionId,
    components: extension.components ? [...extension.components] : undefined,
    componentGroups: extension.componentGroups ? [...extension.componentGroups] : undefined,
    tools: extension.tools ? [...extension.tools] : undefined,
    examples: extension.examples ? [...extension.examples] : undefined,
    additionalRules: extension.additionalRules ? [...extension.additionalRules] : undefined,
    toolProvider: extension.toolProvider ? { ...extension.toolProvider } : undefined,
  };
}

export function createCanvasStore(): CanvasStore {
  const state: CanvasStoreState = {
    canvasCards: [],
    previewTabs: [],
    activeKey: "canvas",
    enableMultiTab: false,
    locale: '',
    theme: '',
    conversationId: '',
    extensions: [],
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
    locale: state.locale,
    theme: state.theme,
    conversationId: state.conversationId,
    extensions: [],

    get hasData(): boolean {
      return state.canvasCards.length > 0 || state.previewTabs.length > 0;
    },

    addExtension(extension): void {
      const clonedExtension = cloneExtensionRegistration(extension);
      const existingIndex = state.extensions.findIndex(
        ext => ext.extensionId === extension.extensionId,
      );
      
      if (existingIndex >= 0) {
        state.extensions[existingIndex] = clonedExtension;
      } else {
        state.extensions.push(clonedExtension);
      }
      
      notify();
    },
    addCanvasCard(card, cardIdOverride): string {
      const { cardId } = card;
      if (!state.enableMultiTab) {
        if (state.previewTabs.length > 0) {
          state.previewTabs = [];
        }
      }

      const resolvedCardId = cardIdOverride ?? cardId ?? generateCardId();
      const existingIndex = cardIdOverride ?? cardId
        ? state.canvasCards.findIndex(c => c.cardId === (cardIdOverride ?? cardId))
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

    removeCanvasTab(): void {
      state.canvasCards = [];
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

    setBaseStore({locale, theme, conversationId}: BaseState) {
      state.locale = locale ?? state.locale;
      state.theme = theme ?? state.theme;
      state.conversationId = conversationId ?? state.conversationId;
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
        locale: state.locale,
        theme: state.theme,
        conversationId: state.conversationId,
        extensions: [...state.extensions]
      };
    },
  };
}

export const canvasStore: ReturnType<typeof createCanvasStore> = createCanvasStore();