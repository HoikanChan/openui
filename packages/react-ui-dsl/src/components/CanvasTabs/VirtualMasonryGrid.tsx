"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Library } from "@openuidev/react-lang";
import { renderElementNode } from "@openuidev/react-lang";
import { Card } from "antd";
import type { DashboardCardData } from "../../canvas/canvasStore";

export interface VirtualMasonryGridProps {
  cards: DashboardCardData[];
  library: Library;
  dataModel?: Record<string, unknown>;
  columns?: number;
  gap?: number;
  buffer?: number;
  estimatedRowHeight?: number;
}

const DEFAULT_COLUMNS = 2;
const DEFAULT_GAP = 16;
const DEFAULT_BUFFER = 300;
const DEFAULT_ESTIMATED_ROW_HEIGHT = 300;

interface CardLayout {
  cardId: string;
  card: DashboardCardData;
  colSpan: number;
  top: number;
  height: number;
  colIndex: number;
  measured: boolean;
}

function colSpanFromWidth(w: number | undefined, totalCols: number): number {
  if (!w) return 1;
  return Math.max(1, Math.min(totalCols, Math.round((w / 12) * totalCols)));
}

export function VirtualMasonryGrid({
  cards,
  library,
  dataModel,
  columns = DEFAULT_COLUMNS,
  gap = DEFAULT_GAP,
  buffer = DEFAULT_BUFFER,
  estimatedRowHeight = DEFAULT_ESTIMATED_ROW_HEIGHT,
}: VirtualMasonryGridProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const measuredHeightsRef = useRef<Map<string, number>>(new Map());
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(0);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    setViewportHeight(el.clientHeight);
    setScrollTop(el.scrollTop);

    const onScroll = () => setScrollTop(el.scrollTop);
    const onResize = () => setViewportHeight(el.clientHeight);

    el.addEventListener("scroll", onScroll);
    const ro = new ResizeObserver(onResize);
    ro.observe(el);

    return () => {
      el.removeEventListener("scroll", onScroll);
      ro.disconnect();
    };
  }, []);

  const layouts = useMemo<CardLayout[]>(() => {
    const colHeights = new Array(columns).fill(0);
    const result: CardLayout[] = [];
    const measured = measuredHeightsRef.current;

    for (const card of cards) {
      const span = colSpanFromWidth(card.size?.w, columns);
      const height = measured.get(card.cardId) ?? estimatedRowHeight;

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

      result.push({
        cardId: card.cardId,
        card,
        colSpan: span,
        top,
        height,
        colIndex: bestCol,
        measured: measured.has(card.cardId),
      });
    }

    return result;
  }, [cards, columns, gap, estimatedRowHeight]);

  const totalHeight = useMemo(() => {
    if (layouts.length === 0) return 0;
    return Math.max(...layouts.map((l) => l.top + l.height)) + gap;
  }, [layouts, gap]);

  const colWidth = 100 / columns;

  const visibleRange = useMemo(() => {
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
  }, [layouts, scrollTop, viewportHeight, buffer]);

  const handleCardMeasured = useCallback((cardId: string, height: number) => {
    measuredHeightsRef.current.set(cardId, height);
  }, []);

  return (
    <div
      ref={scrollRef}
      style={{ height: "100%", overflowY: "auto", padding: gap }}
    >
      <div style={{ position: "relative", height: totalHeight }}>
        {layouts.slice(visibleRange.start, visibleRange.end + 1).map((layout) => (
          <MeasuredCard
            key={layout.cardId}
            cardId={layout.cardId}
            card={layout.card}
            library={library}
            dataModel={dataModel}
            top={layout.top}
            colIndex={layout.colIndex}
            colSpan={layout.colSpan}
            colWidth={colWidth}
            gap={gap}
            estimatedHeight={layout.height}
            onMeasured={handleCardMeasured}
          />
        ))}
      </div>
    </div>
  );
}

function MeasuredCard({
  cardId,
  card,
  library,
  dataModel,
  top,
  colIndex,
  colSpan,
  colWidth,
  gap,
  estimatedHeight,
  onMeasured,
}: {
  cardId: string;
  card: DashboardCardData;
  library: Library;
  dataModel?: Record<string, unknown>;
  top: number;
  colIndex: number;
  colSpan: number;
  colWidth: number;
  gap: number;
  estimatedHeight: number;
  onMeasured: (cardId: string, height: number) => void;
}) {
  const innerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = innerRef.current;
    if (!el) return;
    const h = el.getBoundingClientRect().height;
    if (h > 0 && h !== estimatedHeight) {
      onMeasured(cardId, h);
    }
  }, [cardId, estimatedHeight, onMeasured]);

  useEffect(() => {
    const el = innerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const h = entry.contentRect.height;
        if (h > 0) onMeasured(cardId, h);
      }
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [cardId, onMeasured]);

  const leftPad = colIndex > 0 ? gap / 2 : 0;
  const rightPad = colIndex + colSpan < 100 / colWidth ? gap / 2 : 0;

  return (
    <div
      style={{
        position: "absolute",
        top,
        left: `${colIndex * colWidth}%`,
        width: `${colSpan * colWidth}%`,
        paddingLeft: leftPad,
        paddingRight: rightPad,
      }}
    >
      <div ref={innerRef}>
        <Card title={card.title} style={{ minHeight: estimatedHeight }}>
          {renderElementNode(card.children, library, dataModel)}
        </Card>
      </div>
    </div>
  );
}