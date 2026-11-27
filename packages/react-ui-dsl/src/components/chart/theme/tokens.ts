import { codeToRGB, Gray10 } from './colors';

export const ThemeTokens = {
  dark: {
    // 基础颜色
    TEXT_COLOR: '#eeeeee',
    TITLE_TEXT_COLOR: '#ffffff',
    SUBTITLE_TEXT_COLOR: '#aaaaaa',
    BORDER_COLOR: '#333333',
    LINE_WIDTH: 2,
    SYMBOL_SIZE: 4,
    BORDER_WIDTH: 2,

    // 坐标轴相关
    PRIMARY_TEXT_COLOR: '#FFFFFF',
    SECONDARY_TEXT_COLOR: '#C9C9C9',
    AXIS_LINE_COLOR: codeToRGB(Gray10, 0.1),
    AXIS_TICK_COLOR: '#F3F3F3',
    AXIS_TICK_OPACITY: 0.1,
    AXIS_LABEL_COLOR: '#C9C9C9',
    SPLIT_LINE_COLOR: codeToRGB('#F3F3F3', 0.1),

    // 图例相关
    LEGEND_TEXT_COLOR: '#C9C9C9',
    LEGEND_TEXT_SIZE: 12,

    // 提示框相关
    TOOLTIP_BG_COLOR: '#2A2A2A',
    TOOLTIP_TEXT_COLOR: '#FFFFFF',
    TOOLTIP_TITLE_COLOR: '#FFFFFF',
    TOOLTIP_SHADOW: 'box-shadow: 0 4px 8px rgba(0,0,0,0.4);',

    // 饼图相关
    PIE_LABEL_COLOR: '#FFFFFF',
    PIE_LABEL_SIZE: '0.875rem',
    PIE_LABEL_LINE_COLOR: '#BBBBBB',

    // 仪表盘相关
    GAUGE_PROGRESS_BG_COLOR: 'rgba(80, 80, 80, 0.3)',
    GAUGE_LABEL_COLOR: '#FFFFFF',
    GAUGE_DETAIL_TEXT_COLOR: '#FFFFFF',

    // 背景
    BACKGROUND_COLOR: 'transparent',
  },

  light: {
    // 基础颜色
    TEXT_COLOR: '#333333',
    TITLE_TEXT_COLOR: '#1C1C1C',   // 规范：标题颜色 #1C1C1C
    SUBTITLE_TEXT_COLOR: '#949494', // 规范：副标题颜色 #949494
    BORDER_COLOR: Gray10,
    LINE_WIDTH: 2,
    SYMBOL_SIZE: 4,
    BORDER_WIDTH: 2,

    // 坐标轴相关
    PRIMARY_TEXT_COLOR: '#1C1C1C',
    SECONDARY_TEXT_COLOR: '#949494',
    AXIS_LINE_COLOR: Gray10,
    AXIS_TICK_COLOR: '#1C1C1C',
    AXIS_TICK_OPACITY: 0.1,
    AXIS_LABEL_COLOR: '#949494',   // 规范：坐标文本及单位 #949494
    SPLIT_LINE_COLOR: '#DDDDDD',   // 规范：刻度线 #DDDDDD

    // 图例相关
    LEGEND_TEXT_COLOR: '#707070',  // 规范：图例文本 #707070
    LEGEND_TEXT_SIZE: 12,          // 规范：图例文本 12px

    // 提示框相关
    TOOLTIP_BG_COLOR: '#FFFFFF',
    TOOLTIP_TEXT_COLOR: '#1C1C1C', // 规范：标题文本/合计颜色 #1C1C1C
    TOOLTIP_TITLE_COLOR: '#1C1C1C',
    TOOLTIP_SHADOW: 'box-shadow: 0 4px 8px rgba(0,0,0,0.2);', // 规范：投影 #000000 20% x:0 y:4 模糊:8

    // 饼图相关
    PIE_LABEL_COLOR: '#949494',    // 规范：图例名称文本颜色 #949494
    PIE_LABEL_SIZE: '0.875rem',
    PIE_LABEL_LINE_COLOR: '#676767',

    // 仪表盘相关
    GAUGE_PROGRESS_BG_COLOR: 'rgba(240, 240, 240, 0.8)',
    GAUGE_LABEL_COLOR: '#1C1C1C',
    GAUGE_DETAIL_TEXT_COLOR: '#1C1C1C',

    // 背景
    BACKGROUND_COLOR: '#ffffff',
  },
};
