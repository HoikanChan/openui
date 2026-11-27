import xss from 'xss';
const { filterXSS, FilterXSS, escapeAttrValue, friendlyAttrValue } = xss;

// 创建属性配置的辅助函数
const createAttrs = (base: string[], additional: string[] = []): string[] => {
  return [...base, ...additional];
};

/**
 * 功能描述：将原始HTML内容字符串转换依据配置的xss过滤规则进行过滤
 *
 * @param {string} content 原始HTML内容字符串
 * @return {string} 过滤掉不在白名单的标签、属性后的字符串
 */
export function sanitize(content: string): string {
  // 通用属性集合
  const commonAttrs = ['style', 'class', 'id'];
  const tableAttrs = ['style', 'class', 'id', 'align', 'valign'];

  return filterXSS(content, {
    css: false,
    whiteList: {
      // === 块级元素 ===
      blockquote: createAttrs(commonAttrs),
      div: createAttrs(commonAttrs, ['title']), // 保持原有的 title 属性
      p: createAttrs(commonAttrs),
      pre: createAttrs(commonAttrs),

      // === 标题元素 ===
      h1: createAttrs(commonAttrs),
      h2: createAttrs(commonAttrs),
      h3: createAttrs(commonAttrs),
      h4: createAttrs(commonAttrs),
      h5: createAttrs(commonAttrs),
      h6: createAttrs(commonAttrs),
      
      // === 列表元素 ===
      ol: createAttrs(commonAttrs, ['start']),
      ul: createAttrs(commonAttrs, ['start']),
      li: createAttrs(commonAttrs),
      dl: createAttrs(commonAttrs),
      dt: createAttrs(commonAttrs),
      dd: createAttrs(commonAttrs),
      
      // === 文本格式化元素 ===
      b: createAttrs(commonAttrs),
      strong: createAttrs(commonAttrs),
      i: createAttrs(commonAttrs),
      em: createAttrs(commonAttrs),
      del: createAttrs(commonAttrs),
      s: createAttrs(commonAttrs),
      mark: createAttrs(commonAttrs),
      small: createAttrs(commonAttrs),
      sub: createAttrs(commonAttrs),
      tt: createAttrs(commonAttrs),
      
      // === 行内元素 ===
      span: createAttrs(commonAttrs, ['title']),
      code: createAttrs(commonAttrs),
      br: createAttrs(commonAttrs),
      hr: createAttrs(commonAttrs),
      
      // === 链接和媒体元素 ===
      a: createAttrs(commonAttrs, ['href', 'target', 'rel', 'title']),
      img: createAttrs(commonAttrs, ['src', 'title', 'alt', 'width', 'height']),
      
      // === 表格元素 ===
      table: createAttrs(tableAttrs, ['width', 'border']),
      thead: createAttrs(tableAttrs),
      tbody: createAttrs(tableAttrs),
      tfoot: createAttrs(tableAttrs),
      th: createAttrs(tableAttrs, ['width', 'rowspan', 'colspan']),
      tr: createAttrs(tableAttrs, ['rowspan']),
      td: createAttrs(tableAttrs, ['width', 'rowspan', 'colspan']),
      
      // === 表单元素 ===
      input: ['type', 'checked', 'disabled', 'class', 'id'], // 保持原有配置，表单元素通常不需要style
    },
  });
}

// ─── 危险协议 ───────────────────────────────────────────────
const DANGEROUS_PROTOCOL = /^\s*(javascript|vbscript|data):/i;
// 危险 CSS（url() 可加载外部资源或脚本）
const DANGEROUS_CSS = /(url\s*\(|expression\s*\(|-moz-binding|behavior\s*:)/i;

// ─── 全量 SVG 元素白名单（来源：MDN SVG Element Reference）──
// 排除：script（执行脚本）、foreignObject（嵌入 HTML）、style（注入全局样式）
// use 保留：现代浏览器已限制跨文档引用，href 会走 safeAttrValue 检测
const SVG_ELEMENTS: Record<string, string[]> = {
  // 结构元素
  svg: [], defs: [], g: [], symbol: [], use: [], switch: [],
  // 图形元素
  circle: [], ellipse: [], line: [], path: [],
  polygon: [], polyline: [], rect: [], image: [],
  // 文本元素
  text: [], tspan: [], textPath: [],
  // 渐变元素
  linearGradient: [], radialGradient: [], stop: [], pattern: [],
  // 滤镜元素
  filter: [],
  feBlend: [], feColorMatrix: [], feComponentTransfer: [], feComposite: [],
  feConvolveMatrix: [], feDiffuseLighting: [], feDisplacementMap: [],
  feDropShadow: [], feFlood: [],
  feFuncA: [], feFuncB: [], feFuncG: [], feFuncR: [],
  feGaussianBlur: [], feImage: [], feMerge: [], feMergeNode: [],
  feMorphology: [], feOffset: [], feSpecularLighting: [],
  feTile: [], feTurbulence: [],
  // 其他常用元素
  clipPath: [], mask: [], marker: [], a: [],
  animate: [], animateMotion: [], animateTransform: [], set: [],
  mpath: [], view: [],
  title: [], desc: [], metadata: [],
};

// ─── 明确危险的属性（无论在哪个标签都删除）─────────────────
// 1. 所有事件处理器  2. xlink:href 中的危险协议在 safeAttrValue 里处理
const BLOCKED_ATTRS = new Set([
  // SVG 原生事件属性（非 on* 开头的）
  'externalresourcesrequired',
]);

const svgFilter = new FilterXSS({
  whiteList: SVG_ELEMENTS,
  stripIgnoreTagBody: ['script', 'style'],

  // 不在白名单的标签直接删除
  onIgnoreTag(_tag, _html, _options) {
    return '';
  },

  // 不在白名单的属性走这里（白名单标签上的非白名单属性）
  onIgnoreTagAttr(_tag, name, value) {
    const lowerName = name.toLowerCase();

    if (/^on/i.test(lowerName)) {
      return '';
    }

    if (BLOCKED_ATTRS.has(lowerName)) {
      return '';
    }

    if (['href', 'xlink:href', 'src', 'action'].includes(lowerName)) {
      const decoded = friendlyAttrValue(value);
      if (DANGEROUS_PROTOCOL.test(decoded)) {
        return '';
      }
    }

    if (lowerName === 'style') {
      const decoded = friendlyAttrValue(value);
      if (DANGEROUS_CSS.test(decoded)) {
        return '';
      }
    }

    return `${name}="${escapeAttrValue(value)}"`;
  },

  safeAttrValue(tag, name, value) {
    const lowerName = name.toLowerCase();
    if (['href', 'xlink:href', 'src'].includes(lowerName)) {
      const decoded = friendlyAttrValue(value);
      if (DANGEROUS_PROTOCOL.test(decoded)) {
        return '';
      }
    }
    return escapeAttrValue(value);
  },

  css: false,
});

export function sanitizeSVG(content: string): string {
  return svgFilter.process(content);
}