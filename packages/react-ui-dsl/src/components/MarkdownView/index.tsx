import { marked } from 'marked';
import hljs from 'highlight.js';
import './vendor/hljs-github.css';
import './vendor/github-markdown.css';
import './theme-override.css';
import { sanitize } from './helpers/sanitize';

const EMAIL_PROTO = 'mailto:';
marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    code(token) {
      const highlighted = hljs.highlightAuto(token.text).value;
      return `<pre><code class="hljs">${highlighted}</code></pre>`;
    },
    link(token) {
      // 检查是否是脚注语法 [^footnote]
      if (token.href.startsWith('^') || /^\[\^.*\]$/.test(token.raw)) {
        // 对于脚注，返回原始文本（或者你想要的处理方式）
        return token.raw;
      }

      // 检查是否是markdown链接语法
      // 例如: [text](url) 或 [text](url "title")
      const isLinkSyntax = /^\[.*\]\(.*\)$/.test(token.raw);
      // 检查是否是自动生成的链接: URL autolink: http://example.com
      const isUrlAutoLink = token.text === token.href;
      // 检查是否是自动生成的邮箱链接: Email autolink: user@example.com
      const isEmailAutolink =
        token.href.startsWith(EMAIL_PROTO) && token.text === token.href.replace(EMAIL_PROTO, '');

      if ((isUrlAutoLink || isEmailAutolink) && !isLinkSyntax) {
        // 对于自动链接，直接返回纯文本，不生成<a>标签
        return token.text;
      } else {
        // 对于正常的markdown链接，生成正常的<a>标签
        const href = token.href;
        const title = token.title ? `title="${token.title}"` : '';
        // 使用marked处理parser来解析链接文本，处理链接内容中的Markdown语法
        const text = token.tokens ? this.parser.parseInline(token.tokens) : token.text;
        return `<a href="${href}"${title} target="_blank" rel="noopener noreferrer">${text}</a>`;
      }
    },
  },
});
interface MarkdownProps {
  md: string;
  style?: React.CSSProperties;
  className?: string;
}

export default function Markdown({ md, style, className }: MarkdownProps) {
  const html = marked(md, { async: false });

  return (
    <div
      className={className ? `markdown-body ${className}` : 'markdown-body'}
      dangerouslySetInnerHTML={{ __html: sanitize(html) }}
      style={style}
    />
  );
}

export function MarkdownView({
  content,
  style,
  className,
}: {
  content: string;
  style?: React.CSSProperties;
  className?: string;
}) {
  return <Markdown md={content} style={style} className={className} />;
}
