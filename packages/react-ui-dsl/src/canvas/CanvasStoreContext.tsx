// @ts-nocheck
import { createContext, useContext } from 'react';
import type { CanvasStore } from './canvasStore';

const CanvasStoreContext = createContext<CanvasStore | null>(null);

export function CanvasStoreProvider({ store, children }: { store: CanvasStore; children: React.ReactNode }) {
  return (
    <CanvasStoreContext.Provider value={store}>
      {children}
    </CanvasStoreContext.Provider>
  );
}

export function useCanvasStore(): CanvasStore {
  const store = useContext(CanvasStoreContext);
  if (!store) {
    throw new Error('useCanvasStore must be used within a <CanvasStoreProvider>. Ensure you are using @cloudsop/dsl-engine-web which provides the canvas store.');
  }
  return store;
}
