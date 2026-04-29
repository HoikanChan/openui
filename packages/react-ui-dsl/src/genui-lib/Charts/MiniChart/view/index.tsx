"use client";

import React from "react";
import type * as echarts from "echarts";
import { BaseChart } from "../../../../components/chart/BaseChart";
import {
  getAutoMiniChartHeight,
  getAutoMiniChartWidth,
  getRecentMiniChartDataThatFits,
  normalizeMiniChartData,
  type MiniChartData,
} from "../../view-utils";

const MINI_LINE_SPACING = 20;
const MINI_BAR_SPACING = 14;

type MiniChartType = "line" | "bar" | "area";

type MiniChartCommonProps = {
  data: MiniChartData;
  height?: number | string;
  color?: string;
  size?: number | string;
};

type MiniChartViewProps = MiniChartCommonProps & {
  type: MiniChartType;
};

function getLegacyCompatibleHeight(
  containerWidth: number,
  height?: number | string,
  legacySize?: number | string,
): number | string {
  if (height !== undefined) {
    return height;
  }

  if (typeof legacySize === "number") {
    return legacySize;
  }

  if (typeof legacySize === "string" && !legacySize.trim().endsWith("%")) {
    return legacySize;
  }

  return getAutoMiniChartHeight(containerWidth);
}

function getChartStyle(
  dataLength: number,
  elementSpacing: number,
  containerWidth: number,
  height?: number | string,
  legacySize?: number | string,
): React.CSSProperties {
  const resolvedWidth = getAutoMiniChartWidth(dataLength, elementSpacing, containerWidth);
  return {
    width: resolvedWidth,
    height: getLegacyCompatibleHeight(resolvedWidth, height, legacySize),
  };
}

function buildMiniChartOption(
  points: ReturnType<typeof normalizeMiniChartData>,
  series: echarts.SeriesOption,
  color?: string,
): echarts.EChartsOption {
  return {
    animation: false,
    color: color ? [color] : undefined,
    grid: { top: 4, right: 4, bottom: 4, left: 4, containLabel: false },
    tooltip: { show: false },
    xAxis: {
      type: "category",
      data: points.map(point => point.label),
      show: false,
      boundaryGap: false,
    },
    yAxis: {
      type: "value",
      show: false,
      scale: true,
    },
    series: [series],
  };
}

function useMiniChartPoints(data: MiniChartData, elementSpacing: number) {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = React.useState(0);

  React.useEffect(() => {
    const node = containerRef.current;
    if (!node) {
      return;
    }

    const updateWidth = (width: number) => {
      if (Number.isFinite(width) && width > 0) {
        setContainerWidth(width);
      }
    };

    updateWidth(node.getBoundingClientRect().width);

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        updateWidth(entry.contentRect.width);
      }
    });

    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const visibleData = React.useMemo(
    () => getRecentMiniChartDataThatFits(data, containerWidth, elementSpacing),
    [containerWidth, data, elementSpacing],
  );

  const points = React.useMemo(() => normalizeMiniChartData(visibleData), [visibleData]);

  return { containerRef, containerWidth, points };
}

function MiniChartShell({
  data,
  height,
  color,
  size,
  elementSpacing,
  seriesBuilder,
}: MiniChartCommonProps & {
  elementSpacing: number;
  seriesBuilder: (
    points: ReturnType<typeof normalizeMiniChartData>,
    color?: string,
  ) => echarts.SeriesOption;
}) {
  const { containerRef, containerWidth, points } = useMiniChartPoints(data, elementSpacing);
  const option = React.useMemo(
    () => buildMiniChartOption(points, seriesBuilder(points, color), color),
    [color, points, seriesBuilder],
  );
  const chartStyle = React.useMemo(
    () => getChartStyle(data.length, elementSpacing, containerWidth, height, size),
    [containerWidth, data.length, elementSpacing, height, size],
  );

  return (
    <div ref={containerRef} style={{ width: "100%" }}>
      <div style={chartStyle}>
        <BaseChart option={option} style={{ width: "100%", height: "100%" }} />
      </div>
    </div>
  );
}

export function MiniLineChartView(props: MiniChartCommonProps) {
  return (
    <MiniChartShell
      {...props}
      elementSpacing={MINI_LINE_SPACING}
      seriesBuilder={(points, color) => ({
        type: "line",
        data: points.map(point => point.value),
        smooth: true,
        symbol: "none",
        lineStyle: color ? { color, width: 2 } : { width: 2 },
      })}
    />
  );
}

export function MiniBarChartView(props: MiniChartCommonProps) {
  return (
    <MiniChartShell
      {...props}
      elementSpacing={MINI_BAR_SPACING}
      seriesBuilder={(points, color) => ({
        type: "bar",
        data: points.map(point => point.value),
        barMaxWidth: 8,
        itemStyle: {
          borderRadius: [2, 2, 0, 0],
          ...(color ? { color } : {}),
        },
      })}
    />
  );
}

export function MiniAreaChartView(props: MiniChartCommonProps) {
  return (
    <MiniChartShell
      {...props}
      elementSpacing={MINI_LINE_SPACING}
      seriesBuilder={(points, color) => ({
        type: "line",
        data: points.map(point => point.value),
        smooth: true,
        symbol: "none",
        lineStyle: color ? { color, width: 2 } : { width: 2 },
        areaStyle: color
          ? { color, opacity: 0.2 }
          : { opacity: 0.2 },
      })}
    />
  );
}

export function MiniChartView({ type, ...props }: MiniChartViewProps) {
  if (type === "bar") {
    return <MiniBarChartView {...props} />;
  }

  if (type === "area") {
    return <MiniAreaChartView {...props} />;
  }

  return <MiniLineChartView {...props} />;
}
