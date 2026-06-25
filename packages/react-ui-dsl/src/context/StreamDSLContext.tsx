// @ts-nocheck
import { createContext, useContext, type PropsWithChildren } from "react";
import { createParser } from "@openuidev/react-lang";
import { CanvasStoreProvider } from "../canvas/CanvasStoreContext";
import { canvasStore, type GenUIExtension } from "../canvas/canvasStore";
import { dslLibrary } from "../genui-lib/dslLibrary";
import { createPiu } from "./piu";

export type Locale = 'zh-cn' | 'en-us';
export const DARK_THEME = 'evening';
export const LIGHT_THEME = 'lightday';
export type Theme = typeof LIGHT_THEME | typeof DARK_THEME;

interface AddCardItem {
  data: unknown[];
  title: string;
  id: string;
  type?: string;
  card?: unknown;
}

export interface StreamDSLContextProps {
  locale?: Locale;
  theme?: Theme;
  expandPanelId?: string;
  conversationId?: string;
  handleExpandPanel?: (open: boolean) => void;
  handleConversation?: (msg: string, immediately?: boolean) => void;
}

interface StreamDSLContextValue {
  locale?: Locale;
  theme?: Theme;
  expandPanelId?: string;
  conversationId?: string;
  handleExpandPanel?: (open: boolean) => void;
  handleConversation?: (msg: string, immediately?: boolean) => void;
}

const StreamContext = createContext<StreamDSLContextValue>({});

function parseDslToChildren(dsl: string): unknown[] | null {
  const parser = createParser(dslLibrary.toJSONSchema());
  const result = parser.parse(dsl);
  if (result.root) {
    return [result.root];
  }
  return null;
}

export function StreamDSLContext({
  children,
  locale,
  theme,
  expandPanelId,
  conversationId,
  handleExpandPanel,
  handleConversation,
}: PropsWithChildren<StreamDSLContextProps>) {

  return (
    <StreamContext.Provider value={{ locale, theme, conversationId, expandPanelId, handleExpandPanel, handleConversation }}>
      <IntlProvider locale={language} messages={intl}>
        <CanvasStoreProvider store={canvasStore}>
          {children}
        </CanvasStoreProvider>
      </IntlProvider>
    </StreamContext.Provider>
  );
}

export function useStreamContext() {
  return useContext(StreamContext);
}

let initPromise: Promise<void> | null = null;

export async function init() {
  if (initPromise) {
    return initPromise;
  }

  initPromise = new Promise((resolve) => {
    createPiu((piu) => {
      piu.attach(piu, {
        "smart-canvas:extend": (extension: GenUIExtension) => {
          canvasStore.addExtension(extension);
        },
        "smart-canvas:addCards": (list: unknown) => {
          const cards = list as AddCardItem[];
          cards.forEach(({ data, title, id }) => {
            canvasStore.addPreviewCard(
              { title, children: parseDslToChildren(String(data)) },
              id,
            );
          });
        },
        "smart-canvas:conversation": () => {
          // Conversation forwarding is owned by host integrations.
        },
        "smart-canvas:removeCards": (list: unknown) => {
          const ids = list as string[];
          ids.forEach((id) => {
            canvasStore.removePreviewTab(id);
          });
        },
      });
      resolve();
    });
  });

  return initPromise;
}
