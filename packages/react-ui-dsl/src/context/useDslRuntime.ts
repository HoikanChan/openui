import { useEffect, useMemo, useState } from "react";
import type { Library } from "@openuidev/lang-core";
import { dslLibrary } from "../genui-lib/dslLibrary.js";
import {
  canvasStore,
  CanvasStoreState,
  type GenUIExtension,
  type Tool,
} from "../canvas/canvasStore.js";

export interface DslRuntime {
  library: Library;
  toolProvider: Tool;
}

function useCanvasStore(): CanvasStoreState {
  const [snapshot, setSnapshot] = useState<CanvasStoreState>(() =>
    canvasStore.getSnapshot()
  );

  useEffect(() => {
    const updateSnapshot = () => {
      setSnapshot(canvasStore.getSnapshot());
    };

    const unsubscribe = canvasStore.subscribe(updateSnapshot);

    // 防止 render 到 effect 绑定之间刚好发生了一次注册
    updateSnapshot();

    return unsubscribe;
  }, []);

  return snapshot;
}

function buildRuntimeLibrary(
  baseLibrary: Library,
  extensions: GenUIExtension[],
): Library {
  return extensions.reduce(
    (library, extension) => library.extend(extension),
    baseLibrary,
  );
}

export function useLibrary(lib: Library = dslLibrary): Library {
  const snapshot = useCanvasStore();
  return useMemo(
    () => buildRuntimeLibrary(lib, snapshot.extensions),
    [lib, snapshot.extensions],
  );
}

export function useToolProvider(): Record<string, Tool> {
  const snapshot = useCanvasStore();
  return useMemo(
    () =>
      snapshot.extensions.reduce<Record<string, Tool>>(
        (acc, extension) => ({
          ...acc,
          ...extension.toolProvider,
        }),
        {},
      ),
    [snapshot.extensions],
  );
}