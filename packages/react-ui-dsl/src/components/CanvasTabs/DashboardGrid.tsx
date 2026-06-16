"use client";

import type { Library } from "@openuidev/react-lang";
import type { DashboardCardData } from "../../canvas/canvasStore";
import { VirtualMasonryGrid } from "./VirtualMasonryGrid";

export interface DashboardGridProps {
  cards: DashboardCardData[];
  library: Library;
  dataModel?: Record<string, unknown>;
}

export function DashboardGrid({ cards, library, dataModel }: DashboardGridProps) {
  return <VirtualMasonryGrid cards={cards} library={library} dataModel={dataModel} />;
}