(function () {
  'use strict';

  var DATA = JSON.parse(document.getElementById('bench-data').textContent);
  var CASES = DATA.cases || [];
  var META = DATA.meta || {};
  var STATUS_RANK = {
    'error': 0,
    'timeout': 1,
    'transport-error': 2,
    'still-invalid': 3,
    'invalid': 4,
    'not-reproduced': 5,
    'repaired': 6
  };
  CASES.sort(function (a, b) {
    var ra = STATUS_RANK[a.status];
    var rb = STATUS_RANK[b.status];
    if (ra === undefined) { ra = 9; }
    if (rb === undefined) { rb = 9; }
    return ra - rb || String(a.id).localeCompare(String(b.id));
  });

  function $(sel, root) { return (root || document).querySelector(sel); }

  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text !== undefined && text !== null) { node.textContent = String(text); }
    return node;
  }

  function fmtMs(ms) {
    if (ms === null || ms === undefined || isNaN(ms)) { return '—'; }
    if (ms < 1000) { return ms + ' ms'; }
    return (ms / 1000).toFixed(2) + ' s';
  }

  function normText(text) {
    return String(text || '').replace(/\r\n?/g, '\n');
  }

  var SM = {
    'repaired': { label: '已修复', kind: 'good' },
    'still-invalid': { label: '仍无效', kind: 'bad' },
    'invalid': { label: '仅校验无效', kind: 'bad' },
    'not-reproduced': { label: '未复现', kind: 'bad' },
    'error': { label: '错误', kind: 'bad' },
    'timeout': { label: '超时', kind: 'bad' },
    'transport-error': { label: '传输错误', kind: 'bad' }
  };

  function statusMeta(status) {
    return SM[status] || { label: String(status || '未知'), kind: 'bad' };
  }

  function codeList(codes, emptyText, cls) {
    var box = el('div', 'codes');
    if (!codes || !codes.length) {
      box.appendChild(el('span', 'muted', emptyText));
    } else {
      codes.forEach(function (code) {
        box.appendChild(el('span', 'chip-code ' + (cls || ''), code));
      });
    }
    return box;
  }

  function codeRow(label, content) {
    var row = el('div', 'crow');
    row.appendChild(el('span', 'k', label));
    row.appendChild(content);
    return row;
  }

  /* ── 行级 diff ─────────────────────────────────────────────── */

  function splitLines(text) {
    if (!text) { return []; }
    var lines = normText(text).split('\n');
    while (lines.length && lines[lines.length - 1].trim() === '') { lines.pop(); }
    return lines.map(function (line) { return line.replace(/[ \t]+$/, ''); });
  }

  function lineDiff(a, b) {
    var n = a.length;
    var m = b.length;
    var ops = [];
    if (n * m > 4000000) {
      a.forEach(function (t) { ops.push({ t: 'del', text: t }); });
      b.forEach(function (t) { ops.push({ t: 'add', text: t }); });
      return ops;
    }
    var width = m + 1;
    var dp = new Uint32Array((n + 1) * width);
    for (var i = n - 1; i >= 0; i--) {
      for (var j = m - 1; j >= 0; j--) {
        if (a[i] === b[j]) {
          dp[i * width + j] = dp[(i + 1) * width + j + 1] + 1;
        } else {
          var down = dp[(i + 1) * width + j];
          var right = dp[i * width + j + 1];
          dp[i * width + j] = down >= right ? down : right;
        }
      }
    }
    var x = 0;
    var y = 0;
    while (x < n && y < m) {
      if (a[x] === b[y]) {
        ops.push({ t: 'ctx', text: a[x] });
        x++;
        y++;
      } else if (dp[(x + 1) * width + y] >= dp[x * width + y + 1]) {
        ops.push({ t: 'del', text: a[x] });
        x++;
      } else {
        ops.push({ t: 'add', text: b[y] });
        y++;
      }
    }
    while (x < n) { ops.push({ t: 'del', text: a[x] }); x++; }
    while (y < m) { ops.push({ t: 'add', text: b[y] }); y++; }
    return ops;
  }

  function makeDiffRow(kind, oldNo, newNo, text) {
    var row = el('div', 'diff-row ' + kind);
    row.appendChild(el('span', 'ln', oldNo === null ? '' : String(oldNo)));
    row.appendChild(el('span', 'ln', newNo === null ? '' : String(newNo)));
    row.appendChild(el('span', 'mark', kind === 'del' ? '−' : kind === 'add' ? '+' : ' '));
    row.appendChild(el('span', 'code', text === '' ? ' ' : text));
    return row;
  }

  function renderDiff(beforeText, afterText) {
    var a = splitLines(beforeText);
    var b = splitLines(afterText);
    var ops = lineDiff(a, b);
    var adds = 0;
    var dels = 0;
    ops.forEach(function (op) {
      if (op.t === 'add') { adds++; }
      if (op.t === 'del') { dels++; }
    });

    var wrap = el('div', 'diff');
    var head = el('div', 'diff-head');
    head.appendChild(el('span', null, '原始 → 最终'));
    head.appendChild(el('span', null, a.length + ' → ' + b.length + ' 行'));
    var stat = el('span', 'stat');
    stat.appendChild(el('span', 'sd', '−' + dels));
    stat.appendChild(document.createTextNode('  '));
    stat.appendChild(el('span', 'sa', '+' + adds));
    head.appendChild(stat);
    wrap.appendChild(head);

    if (!adds && !dels) {
      wrap.appendChild(el('div', 'diff-empty', '原始 DSL 与最终 DSL 没有文本差异（' + a.length + ' 行）。'));
      return wrap;
    }

    var rows = [];
    var oldNo = 1;
    var newNo = 1;
    ops.forEach(function (op) {
      if (op.t === 'ctx') {
        rows.push({ kind: 'ctx', o: oldNo++, n: newNo++, text: op.text });
      } else if (op.t === 'del') {
        rows.push({ kind: 'del', o: oldNo++, n: null, text: op.text });
      } else {
        rows.push({ kind: 'add', o: null, n: newNo++, text: op.text });
      }
    });

    var idx = 0;
    while (idx < rows.length) {
      if (rows[idx].kind !== 'ctx') {
        wrap.appendChild(makeDiffRow(rows[idx].kind, rows[idx].o, rows[idx].n, rows[idx].text));
        idx++;
        continue;
      }
      var end = idx;
      while (end < rows.length && rows[end].kind === 'ctx') { end++; }
      var run = end - idx;
      if (run <= 8) {
        for (var k = idx; k < end; k++) {
          wrap.appendChild(makeDiffRow('ctx', rows[k].o, rows[k].n, rows[k].text));
        }
      } else {
        for (var p = idx; p < idx + 2; p++) {
          wrap.appendChild(makeDiffRow('ctx', rows[p].o, rows[p].n, rows[p].text));
        }
        var foldRow = el('div', 'diff-row fold');
        var btn = el('button', 'fold-btn', '⋯ ' + (run - 4) + ' 行未变化 — 展开');
        btn.type = 'button';
        foldRow.appendChild(btn);
        wrap.appendChild(foldRow);
        var body = el('div', 'fold-body');
        body.hidden = true;
        for (var q = idx + 2; q < end - 2; q++) {
          body.appendChild(makeDiffRow('ctx', rows[q].o, rows[q].n, rows[q].text));
        }
        wrap.appendChild(body);
        btn.addEventListener('click', function (hiddenBody, button, hiddenCount) {
          return function () {
            hiddenBody.hidden = !hiddenBody.hidden;
            button.textContent = hiddenBody.hidden
              ? '⋯ ' + hiddenCount + ' 行未变化 — 展开'
              : '⋯ ' + hiddenCount + ' 行未变化 — 收起';
          };
        }(body, btn, run - 4));
        for (var r = end - 2; r < end; r++) {
          wrap.appendChild(makeDiffRow('ctx', rows[r].o, rows[r].n, rows[r].text));
        }
      }
      idx = end;
    }
    return wrap;
  }

  /* ── DSL 语句解析 ──────────────────────────────────────────── */

  function bracketDelta(line) {
    var delta = 0;
    var inString = false;
    var escaped = false;
    for (var i = 0; i < line.length; i++) {
      var ch = line.charAt(i);
      if (inString) {
        if (escaped) { escaped = false; }
        else if (ch === '\\') { escaped = true; }
        else if (ch === '"') { inString = false; }
      } else if (ch === '"') {
        inString = true;
      } else if (ch === '(' || ch === '[' || ch === '{') {
        delta++;
      } else if (ch === ')' || ch === ']' || ch === '}') {
        delta--;
      }
    }
    return delta;
  }

  function splitStatements(text) {
    var lines = normText(text).split('\n');
    var stmts = [];
    var cur = null;
    var depth = 0;
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var trimmed = line.trim();
      var match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*/.exec(line);
      if (depth <= 0 && match) {
        if (cur) { cur.endLine = i - 1; stmts.push(cur); }
        cur = { id: match[1], startLine: i, endLine: i, text: line };
      } else if (cur) {
        if (depth <= 0 && (trimmed === '' || trimmed.indexOf('//') === 0)) {
          cur.endLine = i - 1;
          stmts.push(cur);
          cur = null;
          continue;
        }
        cur.text += '\n' + line;
        cur.endLine = i;
      }
      depth += bracketDelta(line);
      if (depth < 0) { depth = 0; }
    }
    if (cur) {
      cur.endLine = lines.length - 1;
      stmts.push(cur);
    }
    return stmts;
  }

  function refsOf(statementText, idSet) {
    var body = statementText.replace(/^\s*[A-Za-z_][A-Za-z0-9_]*\s*=/, '');
    var refs = {};
    var token = '';
    var tokenStart = -1;
    var inString = false;
    var escaped = false;
    function flush() {
      if (token && idSet[token]) {
        var before = tokenStart > 0 ? body.charAt(tokenStart - 1) : '';
        if (before !== '@') { refs[token] = true; }
      }
      token = '';
      tokenStart = -1;
    }
    for (var i = 0; i < body.length; i++) {
      var ch = body.charAt(i);
      if (inString) {
        if (escaped) { escaped = false; }
        else if (ch === '\\') { escaped = true; }
        else if (ch === '"') { inString = false; }
        continue;
      }
      if (ch === '"') { flush(); inString = true; continue; }
      if (/[A-Za-z0-9_]/.test(ch)) {
        if (!token) { tokenStart = i; }
        token += ch;
      } else {
        flush();
      }
    }
    flush();
    return Object.keys(refs);
  }

  function relatedStatements(dsl, issues) {
    var stmts = splitStatements(dsl);
    var byId = {};
    var idSet = {};
    stmts.forEach(function (stmt) { byId[stmt.id] = stmt; idSet[stmt.id] = true; });

    var deps = {};
    var reverse = {};
    stmts.forEach(function (stmt) {
      var refs = refsOf(stmt.text, idSet).filter(function (id) { return id !== stmt.id; });
      deps[stmt.id] = refs;
      refs.forEach(function (dep) {
        if (!reverse[dep]) { reverse[dep] = []; }
        reverse[dep].push(stmt.id);
      });
    });

    function reach(id, graph) {
      var seen = {};
      var out = [];
      var stack = (graph[id] || []).slice();
      while (stack.length) {
        var next = stack.shift();
        if (seen[next]) { continue; }
        seen[next] = true;
        out.push(next);
        (graph[next] || []).forEach(function (item) { stack.push(item); });
      }
      return out;
    }

    var result = {};
    (issues || []).forEach(function (issue) {
      var id = issue.statementId;
      if (!id || !byId[id] || result[id]) { return; }
      var related = reach(id, deps);
      reach(id, reverse).forEach(function (down) {
        if (related.indexOf(down) === -1) { related.push(down); }
      });
      if (related.length) { result[id] = related; }
    });
    return result;
  }

  /* ── 诊断 ──────────────────────────────────────────────────── */

  function actionableIssues(issues) {
    var seen = {};
    var out = [];
    (issues || []).forEach(function (issue) {
      if (issue.severity && issue.severity !== 'ERROR') { return; }
      var key = [issue.code, issue.statementId, issue.path, issue.message].join('|');
      if (seen[key]) { return; }
      seen[key] = true;
      out.push(issue);
    });
    return out;
  }

  var G = {
    syntax: [
      'Rewrite the whole diagnosed statement instead of patching one token. Keep one complete `name = Expression` per statement.',
      'Arguments are positional: write `Name(value1, value2)`, not `Name(arg: value)`. Keep `key: value` only inside object literals `{...}`. Balance every `(`, `[`, `{`, and string quote.',
      'Arrow functions, template strings, and JavaScript statements are not openui-lang. Use double-quoted strings with `+` concatenation and `@Each`/`@Render` templates.'
    ],
    unknown: 'Use only components listed in the current full contract. Copy the exact component name and positional argument order from its signature; a capitalized call is a component, not a builtin.',
    reference: [
      'Classify the unresolved name from its syntax: `@name(...)` is a builtin call, `Name(...)` is a component call, and a bare identifier is a statement reference. Only builtins take a leading `@`.',
      'If the name is a builtin or action step, add the leading `@`. If it is a statement, define it with that exact id or correct the reference. JavaScript globals are unavailable; replace them with contract builtins. Never invent an external reference, tool, or field.'
    ],
    builtin: 'Match the current contract signature exactly: argument count, positional order, and parameter kind. Binder arguments are double-quoted names and are only in scope inside that builtin\'s template expression.',
    root: 'Keep exactly one reachable `root` statement whose expression is a renderable component call. Redefine `root` if needed; do not delete it.',
    typeData: [
      '`data.<path>` must exist in the caller-supplied data context. Do not invent fields, invent rows, or traverse a field that is not described there.',
      'Do not derive counts, sums, averages, or other statistics from sample rows as if they were the full dataset. Use @builtins over actual data references; if the value cannot be known, render the real known data and state the limitation in the page.'
    ],
    type: [
      'Match the declared prop, operator, chart, and table types. If a value\'s type cannot be proven from the available data, do not assert a definite mismatch; render real data and state the limitation in the page.',
      'Never fabricate data or statistics to satisfy a type. Change the expression to derive from actual references, or show the real available value.'
    ],
    generic: 'Fix only the diagnosed statement and preserve every other definition exactly. Keep all references resolvable and do not invent names, data, or functions.'
  };

  function guidanceFor(issues) {
    var out = [];
    var seen = {};
    function add(value) {
      if (!seen[value]) { seen[value] = true; out.push(value); }
    }
    actionableIssues(issues).forEach(function (issue) {
      var code = issue.code || '';
      var source = issue.source || '';
      if (source === 'syntax' || code.indexOf('syntax-') === 0) {
        G.syntax.forEach(add);
        return;
      }
      if (code.indexOf('unknown-component') === 0) { add(G.unknown); return; }
      if (code === 'unresolved-ref' || source === 'reference') {
        G.reference.forEach(add);
        return;
      }
      if (code.indexOf('builtin-') === 0 ||
          ['excess-args', 'missing-required', 'null-required', 'invalid-prop',
            'empty-component-slot', 'component-slot-type-mismatch'].indexOf(code) !== -1) {
        add(G.builtin);
        return;
      }
      if (code === 'root-missing' || code === 'root-not-renderable') { add(G.root); return; }
      if (code.indexOf('type-data-') === 0) { G.typeData.forEach(add); return; }
      if (code.indexOf('type-') === 0) { G.type.forEach(add); return; }
      add(G.generic);
    });
    return out;
  }

  /* ── 反思 Prompt 还原 ──────────────────────────────────────── */

  var HISTORY_LIMIT = 3;
  var HISTORY_RAW_LIMIT = 2048;

  function lineOffsets(text) {
    var lines = normText(text).split('\n');
    var offsets = [];
    var pos = 0;
    lines.forEach(function (line) {
      offsets.push(pos);
      pos += line.length + 1;
    });
    return offsets;
  }

  function historyExcerpt(raw) {
    if (!raw || raw.length <= HISTORY_RAW_LIMIT) { return raw || ''; }
    var offsets = lineOffsets(raw);
    var stmts = splitStatements(raw);
    var cut = 0;
    stmts.forEach(function (stmt) {
      var line = normText(raw).split('\n')[stmt.endLine] || '';
      var end = offsets[stmt.endLine] + line.length;
      if (end <= HISTORY_RAW_LIMIT) { cut = Math.max(cut, end); }
    });
    if (!cut) {
      var newline = raw.lastIndexOf('\n', HISTORY_RAW_LIMIT - 1);
      cut = newline < 0 ? 0 : newline + 1;
    }
    if (cut <= 0) {
      return '(the first statement of this earlier output exceeds the history excerpt limit; read the current effective DSL instead)';
    }
    return raw.slice(0, cut).replace(/\s+$/, '') +
      '\n[history excerpt truncated at a complete statement boundary; the current effective DSL above is the authoritative state — do not patch from this excerpt alone]';
  }

  function block(lines, text) {
    lines.push('```text');
    lines.push(String(text || '').trim());
    lines.push('```');
    lines.push('');
  }

  function buildPrompt(c, round) {
    var issues = actionableIssues(round.issues);
    var lines = [];
    lines.push('## Original request');
    lines.push(c.intent ? c.intent.trim() : '(no original user message was supplied)');
    lines.push('');
    lines.push('## Current effective DSL (the whole program; last-wins by statement id)');
    lines.push('```openui');
    lines.push(normText(round.currentDefinitions).trim());
    lines.push('```');
    lines.push('');
    lines.push('Every definition not re-emitted stays in force. Only override or append statements.');
    lines.push('');
    lines.push('### Current round raw model output (verbatim)');
    block(lines, round.rawOutput);
    lines.push('## Latest round-end diagnostics (authoritative for this round)');
    if (!issues.length) {
      lines.push('- (no blocking ERROR diagnostics were supplied for the current program)');
    } else {
      issues.forEach(function (issue) {
        var text = '- [' + (issue.code || '(unknown)') + '] ' + (issue.message || '(no message)');
        if (issue.component) { text += ' (component: ' + issue.component + ')'; }
        if (issue.path) { text += ' (at ' + issue.path + ')'; }
        if (issue.statementId || issue.line > 0) {
          text += ' (';
          if (issue.statementId) { text += 'stmt=' + issue.statementId; }
          if (issue.line > 0) {
            if (issue.statementId) { text += ', '; }
            text += 'line ' + issue.line + ':' + Math.max(issue.column || 1, 1);
          }
          text += ')';
        }
        if (issue.hint) { text += '\n  hint: ' + issue.hint; }
        lines.push(text);
      });
    }
    lines.push('');
    lines.push('## Related statements for diagnosed ids (derived from the current AST/scope)');
    var diagnosedIds = [];
    issues.forEach(function (issue) {
      if (issue.statementId && diagnosedIds.indexOf(issue.statementId) === -1) {
        diagnosedIds.push(issue.statementId);
      }
    });
    var related = relatedStatements(round.currentDefinitions, issues);
    var relatedIds = Object.keys(related);
    if (!diagnosedIds.length) {
      lines.push('- (latest diagnostics do not attribute to a statement id)');
    } else if (!relatedIds.length) {
      lines.push('- (no related statements were reported for the diagnosed ids)');
    } else {
      diagnosedIds.forEach(function (id) {
        if (!related[id] || !related[id].length) { return; }
        lines.push('- `' + id + '`: upstream/downstream related statements: ' +
          related[id].map(function (item) { return '`' + item + '`'; }).join(', '));
      });
    }
    lines.push('');
    lines.push('## Earlier round history (compact)');
    var history = c.rounds.slice(0, round.n);
    if (!history.length) {
      lines.push('- (no earlier rounds)');
      return lines.join('\n');
    }
    lines.push('Historical diagnostics describe past states. They never override the current DSL or the latest diagnostics above.');
    lines.push('');
    var start = Math.max(0, history.length - HISTORY_LIMIT);
    if (start > 0) { lines.push('- Earlier rounds omitted: ' + start); }
    for (var index = start; index < history.length; index++) {
      var past = history[index];
      lines.push('### Earlier round ' + (index + 1) + ' — ' +
        (past.status === 'VALID' ? 'VALID' : past.status === 'INVALID' ? 'INVALID' : 'NO CHECK') +
        ' (' + (past.durationMs === null || past.durationMs === undefined ? '?' : past.durationMs) + ' ms)');
      var pastCodes = past.status
        ? (past.codes.length ? past.codes.join(', ') : '(none)')
        : '(none — no round-end check)';
      lines.push('- issue codes: ' + pastCodes);
      lines.push('- raw output:');
      block(lines, historyExcerpt(past.rawOutput));
    }
    return lines.join('\n');
  }

  /* ── 轮次详情 ──────────────────────────────────────────────── */

  function groupIssuesForRound(round) {
    var stmts = splitStatements(round.currentDefinitions);
    var byId = {};
    stmts.forEach(function (stmt) { byId[stmt.id] = stmt; });
    var groups = {};
    var order = [];
    var unattributed = [];
    round.issues.forEach(function (issue) {
      var id = issue.statementId;
      if (!id && issue.line > 0) {
        var hit = stmts.filter(function (stmt) {
          return issue.line >= stmt.startLine + 1 && issue.line <= stmt.endLine + 1;
        })[0];
        if (hit) { id = hit.id; }
      }
      if (id && byId[id]) {
        if (!groups[id]) {
          groups[id] = { id: id, text: byId[id].text, issues: [] };
          order.push(id);
        }
        groups[id].issues.push(issue);
      } else {
        unattributed.push(issue);
      }
    });
    return {
      list: order.map(function (id) { return groups[id]; }),
      unattributed: unattributed,
      statements: stmts
    };
  }

  function issueItem(issue) {
    var li = el('li');
    li.appendChild(el('code', 'issue-code', issue.code || 'unknown'));
    li.appendChild(el('div', 'issue-msg', issue.message || ''));
    var meta = [];
    if (issue.component) { meta.push('component=' + issue.component); }
    if (issue.path) { meta.push('path=' + issue.path); }
    if (issue.line > 0) { meta.push('line ' + issue.line + ':' + Math.max(issue.column || 1, 1)); }
    if (meta.length) { li.appendChild(el('div', 'issue-hint', meta.join(' · '))); }
    if (issue.hint) { li.appendChild(el('div', 'issue-hint', 'hint：' + issue.hint)); }
    return li;
  }

  function renderCodeView(text, hitSpans, hitLines) {
    var wrap = el('div', 'code-view');
    if (!text) {
      wrap.appendChild(el('div', 'muted', '（本轮 DSL 为空）'));
      return wrap;
    }
    normText(text).split('\n').forEach(function (line, index) {
      var n = index + 1;
      var row = el('div', 'cv-row');
      row.appendChild(el('span', 'cv-ln', n));
      row.appendChild(el('span', 'cv-code', line === '' ? ' ' : line));
      var hit = hitSpans.some(function (span) { return n >= span[0] && n <= span[1]; });
      if (hit) { row.classList.add('hit'); }
      if (hitLines[n]) { row.classList.add('hit-line'); }
      wrap.appendChild(row);
    });
    return wrap;
  }

  function pill(status) {
    if (status === 'VALID') { return el('span', 'pill ok', 'VALID'); }
    if (status === 'INVALID') { return el('span', 'pill bad', 'INVALID'); }
    return el('span', 'pill na', status || '—');
  }

  function buildRound(c, round) {
    var details = el('details', 'round');
    var summary = el('summary');
    summary.appendChild(el('span', 'round-name',
      round.n === 0 ? '第 0 轮 · 初始' : '第 ' + round.n + ' 轮 · 修复'));
    summary.appendChild(pill(round.status));
    summary.appendChild(el('span', 'round-meta',
      (round.issues.length ? round.issues.length + ' 个问题' : '无问题') + ' · ' + fmtMs(round.durationMs)));
    details.appendChild(summary);

    var body = el('div', 'round-body');

    body.appendChild(el('h4', 'subhead', '问题语句（本轮 DSL 中的错误）'));
    var grouped = groupIssuesForRound(round);
    if (!grouped.list.length && !grouped.unattributed.length) {
      body.appendChild(el('p', 'muted', '本轮没有诊断到问题语句。'));
    } else {
      grouped.list.forEach(function (group) {
        var box = el('div', 'stmt');
        var head = el('div', 'stmt-head');
        head.appendChild(el('code', null, group.id));
        head.appendChild(el('span', 'muted', group.issues.length + ' 条问题'));
        box.appendChild(head);
        if (group.text) { box.appendChild(el('pre', 'stmt-code', group.text)); }
        var list = el('ul', 'issue-list');
        group.issues.forEach(function (issue) { list.appendChild(issueItem(issue)); });
        box.appendChild(list);
        body.appendChild(box);
      });
      if (grouped.unattributed.length) {
        var box = el('div', 'stmt');
        var head = el('div', 'stmt-head');
        head.appendChild(el('code', null, '（未归属到语句）'));
        head.appendChild(el('span', 'muted', grouped.unattributed.length + ' 条问题'));
        box.appendChild(head);
        var list = el('ul', 'issue-list');
        grouped.unattributed.forEach(function (issue) { list.appendChild(issueItem(issue)); });
        box.appendChild(list);
        body.appendChild(box);
      }
    }

    var hitSpans = grouped.list.map(function (group) {
      var stmt = grouped.statements.filter(function (item) { return item.id === group.id; })[0];
      return stmt ? [stmt.startLine + 1, stmt.endLine + 1] : null;
    }).filter(Boolean);
    var hitLines = {};
    round.issues.forEach(function (issue) {
      if (issue.line > 0) { hitLines[issue.line] = true; }
    });

    var codeDetails = el('details', 'code-details');
    codeDetails.appendChild(el('summary', null,
      '查看本轮累积 DSL（currentDefinitions，' + normText(round.currentDefinitions).split('\n').length + ' 行，高亮问题语句）'));
    codeDetails.appendChild(renderCodeView(round.currentDefinitions, hitSpans, hitLines));
    body.appendChild(codeDetails);

    var rawDetails = el('details', 'code-details');
    rawDetails.appendChild(el('summary', null, '本轮模型原始输出（rawOutput 原文）'));
    rawDetails.appendChild(el('pre', 'patch', round.rawOutput || '（空）'));
    body.appendChild(rawDetails);

    var isLast = round.n === c.rounds.length - 1;
    var note;
    if (isLast) {
      note = round.status === 'VALID'
        ? '本轮校验通过，未生成下一轮修复 Prompt。'
        : '尝试次数或预算已用尽，下一轮 Prompt 未发送；以下按 SDK 模板还原。';
    } else {
      note = '该 Prompt 已用于第 ' + (round.n + 1) + ' 轮修复。';
    }

    var promptDetails = el('details', 'prompt-details');
    promptDetails.appendChild(el('summary', null, '反思 Prompt（本轮诊断 → 下一轮修复）'));
    promptDetails.appendChild(el('div', 'prompt-note',
      note + ' 运行产物未保存 Prompt 原文；此处按 RepairPromptBuilder 模板还原用户消息，「相关语句」为文本引用近似。'));
    var guidance = guidanceFor(round.issues);
    if (guidance.length) {
      promptDetails.appendChild(el('div', 'prompt-sub', '系统消息中的专项指导（按诊断选取，原文）'));
      var guidanceList = el('ul', 'guidance-list');
      guidance.forEach(function (item) { guidanceList.appendChild(el('li', null, item)); });
      promptDetails.appendChild(guidanceList);
    }
    promptDetails.appendChild(el('div', 'prompt-sub', '用户消息（按模板还原）'));
    promptDetails.appendChild(el('pre', 'prompt', buildPrompt(c, round)));
    body.appendChild(promptDetails);

    details.appendChild(body);
    if (round.n === c.rounds.length - 1) { details.open = true; }
    return details;
  }

  /* ── 案例卡片 ──────────────────────────────────────────────── */

  function buildInputSection(c) {
    var section = el('div', 'section input-section');
    section.appendChild(el('h3', null, '原始输入'));

    var intentBox = el('div', 'intent');
    var head = el('div', 'intent-head');
    head.appendChild(el('span', 'k', '原始意图'));
    var source = c.caseSource || {};
    if (source.file || source.run) {
      head.appendChild(el('span', 'src',
        (source.file || '') + (source.run ? ' · ' + source.run : '')));
    }
    intentBox.appendChild(head);
    intentBox.appendChild(el('p', 'intent-text',
      c.intent || '（case.json 中没有 originalIntent）'));
    if (c.rootCause) {
      var cause = el('div', 'root-cause');
      cause.appendChild(el('span', 'k', '根因'));
      cause.appendChild(el('span', null, c.rootCause));
      intentBox.appendChild(cause);
    }
    section.appendChild(intentBox);

    var details = el('details', 'data-details');
    details.open = true;
    var keys = c.dataModel && typeof c.dataModel === 'object' && !Array.isArray(c.dataModel)
      ? Object.keys(c.dataModel) : [];
    details.appendChild(el('summary', null, '原始数据（dataModel）' +
      (keys.length ? ' · ' + keys.length + ' 个顶层字段：' + keys.join('、') : '')));
    if (c.dataModel === null || c.dataModel === undefined) {
      details.appendChild(el('div', 'diff-empty', '（case.json 中没有 dataModel）'));
    } else {
      details.appendChild(el('pre', 'data-json', JSON.stringify(c.dataModel, null, 2)));
    }
    section.appendChild(details);
    return section;
  }

  function buildRoundsTable(c) {
    var table = el('table', 'rounds');
    var thead = el('thead');
    var headRow = el('tr');
    ['轮次', '状态', '问题码', '耗时'].forEach(function (label) {
      headRow.appendChild(el('th', null, label));
    });
    thead.appendChild(headRow);
    table.appendChild(thead);

    var tbody = el('tbody');
    c.rounds.forEach(function (round) {
      var tr = el('tr');
      tr.appendChild(el('td', 'mono', round.n === 0 ? '0 · 初始' : round.n + ' · 修复'));
      var statusCell = el('td');
      statusCell.appendChild(pill(round.status));
      tr.appendChild(statusCell);
      var codesCell = el('td');
      if (round.codes.length) {
        round.codes.forEach(function (code) {
          codesCell.appendChild(el('span', 'chip-code ' + (round.status === 'VALID' ? 'ok' : 'bad'), code));
        });
      } else {
        codesCell.appendChild(el('span', 'muted', round.status === 'VALID' ? '无' : '—'));
      }
      tr.appendChild(codesCell);
      tr.appendChild(el('td', 'mono', fmtMs(round.durationMs)));
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    return table;
  }

  function rawBlock(title, text, note, extraClass) {
    var details = el('details', 'raw' + (extraClass ? ' ' + extraClass : ''));
    var summary = el('summary');
    summary.appendChild(el('b', null, title));
    summary.appendChild(el('span', 'hint', ' ' + note));
    details.appendChild(summary);
    details.appendChild(el('pre', null, text));
    return details;
  }

  function buildCard(c) {
    var sm = statusMeta(c.status);
    var card = el('article', 'card st-' + sm.kind);
    card.setAttribute('data-case', c.id);

    var head = el('div', 'card-head');
    var ident = el('div');
    var title = el('div', 'card-title');
    title.appendChild(el('span', 'case-id', c.id));
    title.appendChild(el('span', 'badge ' + (c.status === 'repaired' ? 'b-good' : 'b-bad'),
      c.status === 'repaired' ? '已修复' : sm.label + ' · 未修复'));
    ident.appendChild(title);
    ident.appendChild(el('div', 'card-sub',
      '案例目录：' + c.dir + '  ·  已复现：' + (c.reproduced ? '是' : '否') +
      '  ·  success 标记：' + (c.success ? 'true' : 'false')));
    head.appendChild(ident);

    var quick = el('div', 'quick');
    function qstat(key, value, cls) {
      var box = el('div', 'q');
      box.appendChild(el('span', 'k', key));
      box.appendChild(el('span', 'v' + (cls ? ' ' + cls : ''), value));
      return box;
    }
    quick.appendChild(qstat('尝试次数', c.attemptCount === null || c.attemptCount === undefined
      ? '—' : c.attemptCount + ' / ' + META.maxAttempts));
    quick.appendChild(qstat('耗时', fmtMs(c.durationMs)));
    quick.appendChild(qstat('结束原因', c.endReason || '—',
      c.endReason === 'CHECK_PASSED' ? 'good' : 'bad'));
    if (c.markdownFallback !== null && c.markdownFallback !== undefined) {
      quick.appendChild(qstat('Markdown 降级', c.markdownFallback ? '是' : '否',
        c.markdownFallback ? 'bad' : ''));
    }
    head.appendChild(quick);
    card.appendChild(head);

    var body = el('div', 'card-body');

    if (c.status !== 'repaired') {
      var banner = el('div', 'banner bad');
      banner.appendChild(el('strong', null, '未修复。'));
      if (c.status === 'still-invalid') {
        banner.appendChild(document.createTextNode(
          '会话在 ' + (c.attemptCount === null || c.attemptCount === undefined ? '?' : c.attemptCount) +
          ' 次尝试后以 ' + (c.endReason || '无 endReason') + ' 结束，最终校验仍有 ' +
          (c.finalCodes && c.finalCodes.length ? c.finalCodes.length + ' 个问题码' : '错误') +
          '。实际输出为 Markdown 降级占位，不是有效页面（降级内容不展示）。'));
      } else if (c.status === 'not-reproduced') {
        banner.appendChild(document.createTextNode(
          '原始 DSL 在基准契约下已通过校验，没有可复现的错误状态，因此未执行修复。' +
          '本案例不计入主修复率，也不计为成功。'));
      } else if (c.status === 'invalid') {
        banner.appendChild(document.createTextNode(
          'validation-only 运行：原始 DSL 校验无效，本次未执行修复（无轮次记录）。' +
          '本案例不计为成功。'));
      } else if (c.status === 'error') {
        banner.appendChild(document.createTextNode('基准执行器报错：'));
        banner.appendChild(el('code', null, c.error || '未知错误'));
      } else if (c.status === 'timeout') {
        banner.appendChild(document.createTextNode('会话或传输在生成有效页面前超时。'));
      } else {
        banner.appendChild(document.createTextNode('会话未以 CHECK_PASSED 结束。'));
      }
      body.appendChild(banner);
    }

    var rows = el('div', 'code-rows');
    var initialEmpty = c.initialCodes.length ? '' : (c.status === 'not-reproduced'
      ? '无 — 原始 DSL 已通过校验' : '无记录');
    rows.appendChild(codeRow('初始问题', codeList(c.initialCodes, initialEmpty, 'bad')));
    var finalEmpty;
    if (c.status === 'not-reproduced' || c.status === 'invalid') {
      finalEmpty = '不适用 — 未执行修复';
    } else if (c.finalCodes && c.finalCodes.length) {
      finalEmpty = '无';
    } else {
      finalEmpty = '无 — 干净';
    }
    rows.appendChild(codeRow('最终问题', codeList(c.finalCodes, finalEmpty,
      c.finalCodes && c.finalCodes.length ? 'bad' : 'ok')));
    rows.appendChild(codeRow('内部问题码', codeList(c.internalCodes,
      '无内部问题码记录', '')));
    body.appendChild(rows);
    body.appendChild(buildInputSection(c));

    var roundsSection = el('div', 'section');
    roundsSection.appendChild(el('h3', null, '验证轮次'));
    if (!c.rounds || !c.rounds.length) {
      roundsSection.appendChild(el('p', 'muted',
        '无轮次记录 — 本次运行未执行修复。'));
    } else {
      var scroll = el('div', 'table-scroll');
      scroll.appendChild(buildRoundsTable(c));
      roundsSection.appendChild(scroll);
      var roundList = el('div', 'round-list');
      c.rounds.forEach(function (round) { roundList.appendChild(buildRound(c, round)); });
      roundsSection.appendChild(roundList);
    }
    body.appendChild(roundsSection);

    var diffSection = el('div', 'section');
    var diffDetails = el('details', 'diff-details');
    diffDetails.open = true;
    var diffSummary = el('summary');
    diffSummary.appendChild(el('span', null, '原始 DSL → 最终 DSL 行级 diff'));
    diffSummary.appendChild(el('span', 'hint',
      '原始：repair-cases/' + c.dir + '/dsl.txt  ·  最终：' +
      (c.status === 'repaired' ? 'JSONL outputDsl'
        : (c.finalDsl ? '最后一次校验的 DSL（finalDsl，未通过校验）' : '无 — 未执行修复'))));
    diffDetails.appendChild(diffSummary);

    if (c.status === 'not-reproduced') {
      diffDetails.appendChild(el('div', 'diff-empty',
        '无 diff — 未执行修复。原始 DSL 见下方。'));
    } else if (!c.finalDsl) {
      diffDetails.appendChild(el('div', 'diff-empty',
        '该案例没有可展示的最终 DSL。'));
    } else {
      if (c.status !== 'repaired') {
        var warn = el('div', 'banner bad');
        warn.appendChild(el('strong', null, '未修复：'));
        warn.appendChild(document.createTextNode(
          '下方 diff 使用最后一次校验的 DSL（finalDsl），它不是合法输出；实际输出为 Markdown 降级占位（不展示）。'));
        diffDetails.appendChild(warn);
      }
      diffDetails.appendChild(renderDiff(c.before, c.finalDsl));
    }
    diffSection.appendChild(diffDetails);
    body.appendChild(diffSection);

    var rawWrap = el('div', 'section');
    rawWrap.appendChild(rawBlock('原始 DSL（修复前）',
      c.before, 'repair-cases/' + c.dir + '/dsl.txt'));
    if (c.finalDsl) {
      rawWrap.appendChild(rawBlock(
        c.status === 'repaired' ? '输出 DSL（修复后）' : '最后一次校验的 DSL（未通过校验）',
        c.finalDsl,
        c.status === 'repaired' ? 'JSONL outputDsl' : 'JSONL finalDsl',
        c.status === 'repaired' ? '' : 'fallback'));
    }
    body.appendChild(rawWrap);

    card.appendChild(body);
    return card;
  }

  /* ── 筛选 ──────────────────────────────────────────────────── */

  var state = { status: 'all', code: 'all', q: '' };
  var entries = [];

  function searchText(c) {
    var parts = [c.id, c.dir, c.intent || '', c.status, c.endReason || '', c.error || '',
      c.rootCause || '', c.before, c.finalDsl,
      c.dataModel ? JSON.stringify(c.dataModel) : '',
      c.initialCodes.join(' '), c.internalCodes.join(' '), c.finalCodes.join(' ')];
    (c.rounds || []).forEach(function (round) {
      parts.push(round.status || '', round.rawOutput || '', round.currentDefinitions || '');
      (round.issues || []).forEach(function (issue) {
        parts.push(issue.code || '', issue.message || '', issue.hint || '', issue.statementId || '');
      });
    });
    return parts.join('\n').toLowerCase();
  }

  function mkChip(label, count, value, on) {
    var button = el('button', 'chip' + (on ? ' on' : ''));
    button.type = 'button';
    button.dataset.value = value;
    button.setAttribute('aria-pressed', on ? 'true' : 'false');
    button.appendChild(el('span', null, label));
    button.appendChild(el('span', 'n', count));
    return button;
  }

  function syncFilterChips(container, active) {
    Array.prototype.forEach.call(container.querySelectorAll('.chip'), function (chip) {
      var on = chip.dataset.value === active;
      chip.classList.toggle('on', on);
      chip.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
  }

  function buildFilters() {
    var statusChips = document.getElementById('status-chips');
    var statuses = [
      ['all', '全部状态'],
      ['repaired', '已修复'],
      ['still-invalid', '仍无效'],
      ['invalid', '仅校验无效'],
      ['not-reproduced', '未复现'],
      ['error', '错误'],
      ['timeout', '超时']
    ];
    statuses.forEach(function (pair) {
      var value = pair[0];
      var count = value === 'all' ? CASES.length : CASES.filter(function (c) { return c.status === value; }).length;
      var chip = mkChip(pair[1], count, value, value === 'all');
      chip.addEventListener('click', function () {
        state.status = value;
        syncFilterChips(statusChips, value);
        apply();
      });
      statusChips.appendChild(chip);
    });

    var codeChips = document.getElementById('code-chips');
    var counts = {};
    CASES.forEach(function (c) {
      (c.initialCodes || []).forEach(function (code) {
        counts[code] = (counts[code] || 0) + 1;
      });
    });
    var codes = Object.keys(counts).sort(function (x, y) {
      return counts[y] - counts[x] || x.localeCompare(y);
    });
    var allChip = mkChip('全部初始问题码', CASES.length, 'all', true);
    allChip.addEventListener('click', function () {
      state.code = 'all';
      syncFilterChips(codeChips, 'all');
      apply();
    });
    codeChips.appendChild(allChip);
    codes.forEach(function (code) {
      var chip = mkChip(code, counts[code], code, false);
      chip.addEventListener('click', function () {
        state.code = code;
        syncFilterChips(codeChips, code);
        apply();
      });
      codeChips.appendChild(chip);
    });
  }

  function apply() {
    var shown = 0;
    entries.forEach(function (entry) {
      var c = entry.card;
      var ok = (state.status === 'all' || c.status === state.status) &&
        (state.code === 'all' || (c.initialCodes || []).indexOf(state.code) !== -1) &&
        (!state.q || entry.search.indexOf(state.q) !== -1);
      entry.node.hidden = !ok;
      if (ok) { shown++; }
    });
    document.getElementById('count').textContent =
      '显示 ' + shown + ' / ' + CASES.length + ' 个案例';
    document.getElementById('empty').hidden = shown !== 0;
  }

  /* ── 汇总头部 ──────────────────────────────────────────────── */

  function pctText(ok, denominator) {
    if (!denominator) { return 'n/a'; }
    return (100 * ok / denominator).toFixed(2) + '%';
  }

  function setText(id, value) {
    var node = document.getElementById(id);
    if (node) { node.textContent = value === null || value === undefined ? '' : String(value); }
  }

  function renderHeader() {
    var by = META.byStatus || {};
    var total = META.total === null || META.total === undefined ? CASES.length : Number(META.total);
    var repaired = Number(by.repaired || 0);
    var still = Number(by['still-invalid'] || 0);
    var invalid = Number(by.invalid || 0);
    var notReproduced = Number(by['not-reproduced'] || 0);
    var failed = Number(by.error || 0) + Number(by.timeout || 0) + Number(by['transport-error'] || 0);

    setText('hdr-model', META.model || '未知');
    setText('hdr-model2', META.model || '未知');
    setText('hdr-source', META.sourceFile || 'unknown');
    setText('hdr-generated', DATA.generatedAt || '');
    setText('hdr-attempts', META.maxAttempts === null || META.maxAttempts === undefined
      ? '—' : META.maxAttempts);
    setText('hdr-budget', META.sessionBudgetSeconds === null || META.sessionBudgetSeconds === undefined
      ? '—' : META.sessionBudgetSeconds + ' s');
    setText('hdr-concurrency', META.concurrency === null || META.concurrency === undefined
      ? '—' : META.concurrency);
    setText('hdr-timeout', META.timeoutMinutes ? META.timeoutMinutes + ' 分钟' : '不限');
    setText('hdr-duration', fmtMs(META.durationMs));

    setText('tile-total', total);
    setText('tile-repaired', repaired);
    setText('tile-repaired-sub', '占全部案例 ' + pctText(repaired, total));
    setText('tile-still', still);
    setText('tile-notrep', notReproduced);
    var invalidTile = document.getElementById('tile-invalid');
    if (invalid > 0) {
      invalidTile.hidden = false;
      setText('tile-invalid-v', invalid);
    } else {
      invalidTile.hidden = true;
    }

    var main = META.mainRate || {};
    var mainOk = main.success === null || main.success === undefined ? repaired : Number(main.success);
    var mainDen = main.denominator === null || main.denominator === undefined ? total : Number(main.denominator);
    var secondary = META.secondaryRate || {};
    var secOk = secondary.success === null || secondary.success === undefined ? repaired : Number(secondary.success);
    var secDen = secondary.denominator === null || secondary.denominator === undefined
      ? total : Number(secondary.denominator);
    setText('tile-rate', pctText(mainOk, mainDen));
    setText('tile-rate-sub', mainOk + ' / ' + mainDen + ' · 次要口径 ' +
      secOk + '/' + secDen + ' = ' + pctText(secOk, secDen));

    var stack = document.getElementById('stack');
    stack.textContent = '';
    function segment(count, cls, label) {
      if (!count || !total) { return; }
      var part = el('span', cls);
      part.style.width = (100 * count / total).toFixed(3) + '%';
      part.title = label + '：' + count;
      stack.appendChild(part);
    }
    segment(repaired, 'sg', '已修复');
    segment(still + invalid + failed, 'sr', '未修复');
    segment(notReproduced, 'sa', '未复现');

    var legend = document.getElementById('legend');
    legend.textContent = '';
    function legendItem(cls, count, label) {
      if (!count) { return; }
      var item = el('span');
      item.appendChild(el('i', cls));
      item.appendChild(el('b', null, count));
      item.appendChild(document.createTextNode(' ' + label));
      legend.appendChild(item);
    }
    legendItem('sg', repaired, '已修复（CHECK_PASSED，最终 DSL 通过校验）');
    legendItem('sr', still, '仍无效（Markdown 降级 — 未修复）');
    legendItem('sr', invalid, '仅校验无效（validation-only — 未修复）');
    legendItem('sr', failed, '错误/超时（未修复）');
    legendItem('sa', notReproduced, '未复现（不计入主修复率）');

    var tbody = document.getElementById('bucket-body');
    tbody.textContent = '';
    var buckets = META.issueBuckets || {};
    Object.keys(buckets).sort(function (x, y) {
      return (Number(buckets[y].cases || 0) - Number(buckets[x].cases || 0)) || x.localeCompare(y);
    }).forEach(function (code) {
      var bucket = buckets[code] || {};
      var cases = Number(bucket.cases || 0);
      var ok = Number(bucket.repaired || 0);
      var unresolved = cases - ok;
      var rate = cases ? 100 * ok / cases : 0;
      var row = el('tr');
      row.appendChild(el('td', 'code', code));
      row.appendChild(el('td', 'num', cases));
      row.appendChild(el('td', 'num', ok));
      row.appendChild(el('td', 'num', unresolved));
      var rateCell = el('td');
      var wrap = el('div', 'bar-wrap');
      var bar = el('div', 'bar');
      var fill = el('i');
      fill.style.width = rate.toFixed(1) + '%';
      bar.appendChild(fill);
      wrap.appendChild(bar);
      wrap.appendChild(el('span', 'pct', Math.round(rate) + '%'));
      rateCell.appendChild(wrap);
      row.appendChild(rateCell);
      tbody.appendChild(row);
    });

    setText('foot-source', DATA.sourcePath || META.sourceFile || 'unknown');
    setText('foot-model', META.model || '未知');
    setText('foot-generated', DATA.generatedAt || '');
  }

  /* ── 启动 ──────────────────────────────────────────────────── */

  renderHeader();

  var list = document.getElementById('cards');
  CASES.forEach(function (c) {
    var node = buildCard(c);
    list.appendChild(node);
    entries.push({ card: c, node: node, search: searchText(c) });
  });

  buildFilters();

  var search = document.getElementById('search');
  search.addEventListener('input', function () {
    state.q = search.value.trim().toLowerCase();
    apply();
  });
  search.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      search.value = '';
      state.q = '';
      apply();
    }
  });

  document.getElementById('reset').addEventListener('click', function () {
    state.status = 'all';
    state.code = 'all';
    state.q = '';
    search.value = '';
    syncFilterChips(document.getElementById('status-chips'), 'all');
    syncFilterChips(document.getElementById('code-chips'), 'all');
    apply();
  });

  var expandAll = document.getElementById('expand-all');
  if (expandAll) {
    expandAll.addEventListener('click', function () {
      Array.prototype.forEach.call(document.querySelectorAll('details.diff-details'), function (d) {
        d.open = true;
      });
    });
  }
  var collapseAll = document.getElementById('collapse-all');
  if (collapseAll) {
    collapseAll.addEventListener('click', function () {
      Array.prototype.forEach.call(document.querySelectorAll('details.diff-details'), function (d) {
        d.open = false;
      });
    });
  }

  apply();
})();
