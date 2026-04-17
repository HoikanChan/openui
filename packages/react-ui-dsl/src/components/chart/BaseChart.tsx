// packages/react-ui-dsl/src/components/chart/BaseChart.tsx
import * as echarts from "echarts";
import React from "react";
import { lightTheme } from "./theme";

const THEME_NAME = "openui-dsl";
let themeRegistered = false;

function ensureTheme() {
  if (!themeRegistered) {
    echarts.registerTheme(THEME_NAME, lightTheme);
    themeRegistered = true;
  }
}

interface BaseChartProps {
  option: echarts.EChartsOption;
  style?: React.CSSProperties;
}

export const BaseChart: React.FC<BaseChartProps> = ({ option, style }) => {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const chartRef = React.useRef<echarts.ECharts | null>(null);
  const optionRef = React.useRef(option);
  optionRef.current = option;

  // Init — also applies the current option so the chart renders immediately
  React.useEffect(() => {
    ensureTheme();
    if (!containerRef.current) return;
    const chart = echarts.init(containerRef.current, THEME_NAME);
    chartRef.current = chart;
    chart.setOption(optionRef.current, true);
    return () => {
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  // Update option when it changes after mount
  React.useEffect(() => {
    chartRef.current?.setOption(option, true);
  }, [option]);

  // Resize
  React.useEffect(() => {
    const observer = new ResizeObserver(() => {
      chartRef.current?.resize();
    });
    if (containerRef.current) observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={containerRef}
      style={{ width: "100%", height: 300, ...style }}
    />
  );
};
