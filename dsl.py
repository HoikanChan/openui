# TextDSL
TEXT_DSL = """
interface TextDSL {
  type: 'text';
  properties: {
    type?: 'default' | 'markdown' | 'html';
    content: string;
  };
  style?: CSSProperties;
}
"""

# 基础组件DSL定义
BASIC_COMPONENT_DSL = """
{TEXT_DSL}

interface ButtonDSL {
  type: 'button';
  style?: CSSProperties;
  properties?: {
    status?: 'default' | 'primary' | 'risk';
    disabled?: boolean;
    text?: string;
    type?: 'default' | 'text';
  };
  actions?: Action[];
}

interface SelectDSL {
  type: 'select';
  properties: {
    allowClear?: boolean;
    options: {
      label: string;
      value: number | string;
    }[];
    defaultValue?: number | string;
  };
  style?: CSSProperties;
}

interface ImageDSL {
  type: 'image';
  properties: {
    type: 'url' | 'base64' | 'svg';
    content: string;
  };
  style?: CSSProperties;
}

interface LinkDSL {
  type: 'link';
  properties: {
    target?: '_self' | '_blank';
    href: string;
    text?: string;
    disabled?: boolean;
    download?: string;
  };
  style?: CSSProperties;
}

interface CardDSL {
  type: 'card';
  properties: {
    tag?: string;
    header?: string;
    headerAlign?: 'left' | 'center' | 'right';
  };
  children: DSL[];
  style?: CSSProperties;
}

interface ListDSL {
  type: 'list';
  properties: {
    header?: string;
    isOrder?: boolean;
  };
  children: DSL[];
  style?: CSSProperties;
}
"""

# 布局组件DSL定义
LAYOUT_COMPONENT_DSL = """
// 水平布局组件
interface HLayoutDSL {
  type: 'hLayout';
  properties: {
    gap?: number;
    wrap?: boolean;
  };
  children: DSL[];
  style?: CSSProperties;
}

// 垂直布局组件
interface VLayoutDSL {
  type: 'vLayout';
  properties?: {
    gap?: number;
  };
  children?: DSL[];
  style?: CSSProperties;
  actions?: Action[];
}
"""

# 表单组件DSL定义
FORM_COMPONENT_DSL = """
interface FormDSL {
  type: 'form';
  properties: {
    layout?: 'vertical' | 'inline' | 'horizontal';
    labelAlign?: 'left' | 'right';
    initialValues?: Record<string, unknown>;
    fields: {
      label: string;
      name: string;
      rules?: { required: boolean }[];
      component: DSL;
    }[];
  };
}
"""

# 表格组件DSL定义
TABLE_COMPONENT_DSL = """
interface TableDSL {
  type: 'table';
  properties: {
    columns: {
      title: string;
      field: string;
      sortable?: boolean; // 考虑给数值列添加排序功能，但不要给所有列都添加排序功能
      filterable?: boolean;
      filterOptions?: string[];
      customized?: DSL;
      format?: 'data' | 'dateTime' | 'time';
      tooltip?: boolean;
    }[];
  };
  style?: CSSProperties;
}
"""

# 图表组件DSL定义
PIE_CHART_DSL = """
interface PieChartProperties extends Omit<echarts.EChartsOption, 'title'> {
  title?: string;
  series?: Array<PieSeriesOption>;
}
interface PieChartDSL {
  type: 'pieChart';
  properties: PieChartProperties;
  data?: { source: number[][] };
  style?: CSSProperties;
}
"""

LINE_CHART_DSL = """
interface LineChartProperties extends Omit<echarts.EChartsOption, 'title'> {
  title?: string;
  series?: Array<LineSeriesOption>;
}

interface LineChartDSL {
  type: 'lineChart';
  properties: LineChartProperties;
  data?: { source: number[][] };
  style?: CSSProperties;
}
"""

BAR_CHART_DSL = """
interface BarChartProperties extends Omit<echarts.EChartsOption, 'title'> {
  title?: string;
  series?: Array<BarSeriesOption>;
}

interface BarChartDSL {
  type: 'barChart';
  properties: BarChartProperties;
  style?: CSSProperties;
}
"""

GAUGE_CHART_DSL = """
interface GaugeChartProperties extends Omit<echarts.EChartsOption, 'title'> {
  title?: string;
  series?: Array<GaugeSeriesOption>;
}
interface GaugeChartDSL {
  type: 'gaugeChart';
  properties: GaugeChartProperties;
  data?: { source: number[][] };
  style?: CSSProperties;
}
"""

# 时间线组件DSL定义
TIMELINE_COMPONENT_DSL = """
interface TimeLineDSL {
  type: 'timeLine';
  properties: {
    title?: string;
    id?: string;
  };
  data: {
    content: {
      title: string;
      children: DSL[];
    };
    iconType: 'success' | 'error' | 'default';
  }[];
}
"""
