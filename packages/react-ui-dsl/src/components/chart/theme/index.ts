import { palette } from "./colors";
import { darkTokens, lightTokens, type ThemeTokens } from "./tokens";

function buildTheme(tokens: ThemeTokens) {
  return {
    color: palette,
    backgroundColor: tokens.backgroundColor,
    textStyle: {
      color: tokens.textColor,
    },
    title: {
      textStyle: {
        color: tokens.textColor,
      },
    },
    legend: {
      textStyle: {
        color: tokens.textColor,
      },
    },
    tooltip: {
      backgroundColor: tokens.backgroundColor,
      borderColor: tokens.borderColor,
      textStyle: {
        color: tokens.textColor,
      },
    },
    categoryAxis: {
      axisLine: { lineStyle: { color: tokens.borderColor } },
      axisTick: { lineStyle: { color: tokens.borderColor } },
      axisLabel: { color: tokens.textColor },
      splitLine: { lineStyle: { color: tokens.splitLineColor } },
    },
    valueAxis: {
      axisLine: { lineStyle: { color: tokens.borderColor } },
      axisTick: { lineStyle: { color: tokens.borderColor } },
      axisLabel: { color: tokens.textColor },
      splitLine: { lineStyle: { color: tokens.splitLineColor } },
    },
  };
}

export const lightTheme = buildTheme(lightTokens);
export const darkTheme = buildTheme(darkTokens);
