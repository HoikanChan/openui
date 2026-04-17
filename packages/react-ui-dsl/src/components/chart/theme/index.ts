import colorGroup from './colors';
import { ThemeTokens } from './tokens';

const createTheme = (themeColors: typeof ThemeTokens.light, isDark: boolean = false) => {
  // 公共坐标轴基础配置（category / time 轴共用）
  const baseAxisConfig = {
    axisLine: {
      show: true,
      lineStyle: {
        color: themeColors.AXIS_LINE_COLOR,
      },
    },
    axisTick: {
      show: true,
      lineStyle: {
        color: themeColors.AXIS_TICK_COLOR,
        opacity: themeColors.AXIS_TICK_OPACITY,
        width: 2,
      },
      length: 1,
    },
    axisLabel: {
      show: true,
      color: themeColors.AXIS_LABEL_COLOR,
    },
    splitLine: {
      show: false,
      lineStyle: {
        color: [themeColors.SPLIT_LINE_COLOR],
        type: 'dashed' as const,
      },
    },
  };

  return {
    darkMode: isDark,
    color: colorGroup,
    backgroundColor: themeColors.BACKGROUND_COLOR,
    textStyle: {
      color: themeColors.TEXT_COLOR,
    },

    // 标题：规范 #1C1C1C 20px / 副标题 #949494
    title: {
      textStyle: {
        color: themeColors.TITLE_TEXT_COLOR,
        fontSize: 20,
      },
      subtextStyle: {
        color: themeColors.SUBTITLE_TEXT_COLOR,
      },
    },

    // Category 轴
    categoryAxis: baseAxisConfig,

    // Time 轴（补全，与 category 轴视觉一致）
    timeAxis: {
      ...baseAxisConfig,
      axisLabel: {
        show: true,
        color: themeColors.AXIS_LABEL_COLOR,
        hideOverlap: true, // 自动隐藏重叠标签
      },
    },

    // Log 轴（补全，开启 splitLine）
    logAxis: {
      ...baseAxisConfig,
      splitLine: {
        show: true,
        lineStyle: {
          color: [themeColors.SPLIT_LINE_COLOR],
          type: 'dashed' as const,
        },
      },
    },

    // Value 轴：规范 splitNumber=4，虚线分割线
    valueAxis: {
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        show: true,
        color: themeColors.AXIS_LABEL_COLOR, // 规范：#949494
        fontSize: 12,                         // 规范：12px
      },
      splitLine: {
        show: true,
        lineStyle: {
          color: [themeColors.SPLIT_LINE_COLOR], // 规范：#DDDDDD
          type: 'dashed' as const,               // 规范：虚线
        },
      },
      splitNumber: 4, // 规范：4段
    },

    // 图例：规范 #707070 12px，居中底部
    legend: {
      textStyle: {
        color: themeColors.LEGEND_TEXT_COLOR, // 规范：#707070
        fontSize: themeColors.LEGEND_TEXT_SIZE, // 规范：12px
      },
      left: 'center',
      bottom: 10,
      orient: 'horizontal' as const,
      itemGap: 20,
    },

    // 提示框：规范 背景#FFFFFF，投影 0 4px 8px rgba(0,0,0,0.2)，标题#1C1C1C
    tooltip: {
      backgroundColor: themeColors.TOOLTIP_BG_COLOR,
      textStyle: {
        color: themeColors.TOOLTIP_TEXT_COLOR, // 规范：#1C1C1C
        fontWeight: 'normal' as const,
        fontSize: 14,
      },
      extraCssText: themeColors.TOOLTIP_SHADOW, // 规范：box-shadow: 0 4px 8px rgba(0,0,0,0.2)
    },

    // 线形图：规范粗细 2px
    line: {
      itemStyle: {
        borderWidth: themeColors.BORDER_WIDTH,
      },
      lineStyle: {
        width: themeColors.LINE_WIDTH, // 规范：2px
      },
      symbolSize: themeColors.SYMBOL_SIZE,
      symbol: 'emptyCircle',
      smooth: true,
    },

    // 柱状图
    bar: {
      itemStyle: {
        barBorderWidth: themeColors.BORDER_WIDTH,
        barBorderColor: themeColors.BORDER_COLOR,
      },
    },

    // 饼图：规范 图例名称 #949494
    pie: {
      itemStyle: {
        borderWidth: themeColors.BORDER_WIDTH,
        borderColor: themeColors.BORDER_COLOR,
        borderRadius: 2,
      },
      radius: ['50%', '60%'],
      label: {
        color: themeColors.PIE_LABEL_COLOR, // 规范：#949494
        fontSize: themeColors.PIE_LABEL_SIZE,
        textBorderWidth: 0,
        fontWeight: 'normal' as const,
      },
      labelLine: {
        lineStyle: {
          color: themeColors.PIE_LABEL_LINE_COLOR,
        },
      },
    },

    // 仪表盘
    gauge: {
      splitNumber: 4,
      radius: '100%',
      center: ['50%', '50%'],
      backgroundColor: 'transparent',
      axisLine: {
        roundCap: true,
        lineStyle: {
          width: 15,
          color: [
            [1, themeColors.GAUGE_PROGRESS_BG_COLOR],
          ],
        },
      },
      progress: {
        show: true,
        roundCap: true,
        width: 15,
        itemStyle: {
          borderRadius: 7,
        },
      },
      axisTick: {
        show: false,
      },
      splitLine: {
        show: false,
      },
      axisLabel: {
        distance: 16,
        color: themeColors.SECONDARY_TEXT_COLOR,
        fontSize: 14,
      },
      pointer: {
        show: true,
        icon: 'path://M4.49 10.21L0.1 1.44C-0.23 0.78 0.25 0 1 0L9.76 0C10.5 0 10.99 0.78 10.65 1.44C9.14 4.47 7.33 8.09 6.28 10.21C5.91 10.94 4.86 10.94 4.49 10.21Z',
        length: '5%',
        width: 16,
        offsetCenter: [0, '-105%'],
      },
      title: {
        fontSize: 14,
        color: themeColors.GAUGE_LABEL_COLOR,
      },
      detail: {
        valueAnimation: true,
        color: themeColors.GAUGE_LABEL_COLOR,
        formatter: function formatter(value: number) {
          return '{value|' + value.toFixed(0) + '}';
        },
        rich: {
          value: {
            fontSize: 60,
            fontWeight: 'bolder',
            color: themeColors.GAUGE_DETAIL_TEXT_COLOR,
          },
          name: {
            fontSize: 20,
            color: themeColors.GAUGE_DETAIL_TEXT_COLOR,
            padding: [24, 0, 0, 0],
          },
          unit: {
            color: themeColors.GAUGE_DETAIL_TEXT_COLOR,
          },
        },
        offsetCenter: [0, '0%'],
        style: {
          textAlign: 'center',
        },
      },
      itemStyle: {
        borderWidth: themeColors.BORDER_WIDTH,
        borderColor: themeColors.BORDER_COLOR,
      },
    },
  };
};

export const darkTheme = createTheme(ThemeTokens.dark as any, true);
export const lightTheme = createTheme(ThemeTokens.light);
