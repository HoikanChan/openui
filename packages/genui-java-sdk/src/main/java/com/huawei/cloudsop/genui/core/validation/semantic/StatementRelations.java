/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 错误语句的上下游关联（#58）。
 *
 * <p>
 * 以当前有效程序（同名 last-wins）的 AST 为准建立语句依赖图：语句里出现的、指向另一条语句的 {@code Ref} 即一条边； {@code @Each}/{@code @Render} 的 binder
 * 名在模板参数内保持词法作用域，作用域内的同名引用不计为语句依赖——不使用文本变量名匹配。 上游 = 被诊断语句（传递）依赖的定义；下游 = （传递）依赖被诊断语句的定义。关联仅用于修复提示词定位原因，不是修改授权白名单。
 *
 * @since 2026
 */
public final class StatementRelations {

    private StatementRelations() {
    }

    /**
     * 计算被诊断语句 id 的传递上下游语句。
     *
     * @param currentDsl
     *            当前有效定义文本；{@code null}/空文本返回空 map
     * @param issues
     *            最新轮诊断；仅使用可归属到语句 id 的问题
     * @return 语句 id → 关联 id 列表（上游在前、下游在后）；被诊断但无关联时为空列表
     */
    public static Map<String, List<String>> related(String currentDsl, List<ValidationIssue> issues) {
        return related(currentDsl, issues, BuiltinCatalog.getDefault());
    }

    /**
     * 按指定 {@link BuiltinCatalog} 计算被诊断语句 id 的传递上下游语句。
     */
    public static Map<String, List<String>> related(String currentDsl, List<ValidationIssue> issues,
            BuiltinCatalog builtins) {
        if (currentDsl == null || currentDsl.isBlank() || issues == null || issues.isEmpty()) {
            return Map.of();
        }
        BuiltinCatalog effectiveBuiltins = builtins == null ? BuiltinCatalog.getDefault() : builtins;
        Program program = OpenuiParser.parse(currentDsl, ParseMode.FINAL, effectiveBuiltins);
        LinkedHashMap<String, Statement> current = program.currentById();
        if (current.isEmpty()) {
            return Map.of();
        }

        Map<ScopedStatement, Set<ScopedStatement>> dependencies = directDependencies(current, effectiveBuiltins);
        Map<ScopedStatement, Set<ScopedStatement>> dependents = reverse(dependencies);

        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (ValidationIssue issue : issues) {
            String id = issue.statementId();
            if (id == null || id.isBlank() || !current.containsKey(id) || result.containsKey(id)) {
                continue;
            }
            Set<String> upstream = reachable(id, dependencies);
            Set<String> downstream = reachable(id, dependents);
            LinkedHashSet<String> related = new LinkedHashSet<>(upstream);
            related.addAll(downstream);
            result.put(id, List.copyOf(related));
        }
        return Collections.unmodifiableMap(result);
    }

    /** A named template can have different dependencies at different lexical use sites. */
    private record ScopedStatement(String id, Set<String> bindings) {
        private ScopedStatement {
            bindings = Set.copyOf(bindings);
        }
    }

    private static Map<ScopedStatement, Set<ScopedStatement>> directDependencies(Map<String, Statement> statements,
            BuiltinCatalog builtins) {
        LinkedHashMap<ScopedStatement, Set<ScopedStatement>> dependencies = new LinkedHashMap<>();
        // Resolve from the page first. Extracted templates inherit use-site bindings, not an artificial empty scope.
        String entry = statements.containsKey("root")
                ? "root"
                : statements.entrySet().stream()
                        .filter(e -> e.getValue() instanceof Statement.Value value
                                && value.expr() instanceof AstNode.Comp comp && !builtins.isBuiltin(comp.name())
                                && !BuiltinCatalog.isReservedCall(comp.name()))
                        .map(Map.Entry::getKey).findFirst().orElse(statements.keySet().iterator().next());
        visit(new ScopedStatement(entry, Set.of()), statements, builtins, dependencies);

        for (String id : statements.keySet()) {
            if (dependencies.keySet().stream().noneMatch(key -> key.id().equals(id))) {
                visit(new ScopedStatement(id, Set.of()), statements, builtins, dependencies);
            }
        }
        return dependencies;
    }

    private static void visit(ScopedStatement statement, Map<String, Statement> statements, BuiltinCatalog builtins,
            Map<ScopedStatement, Set<ScopedStatement>> dependencies) {
        if (dependencies.containsKey(statement)) {
            return;
        }
        LinkedHashSet<ScopedStatement> refs = new LinkedHashSet<>();
        dependencies.put(statement, refs);
        AstReferenceWalker.walkDependencies(statements.get(statement.id()).expr(), builtins, statement.bindings(),
                (name, scope) -> {
                    if (statements.containsKey(name)) {
                        refs.add(new ScopedStatement(name, scope));
                    }
                });
        for (ScopedStatement ref : refs) {
            visit(ref, statements, builtins, dependencies);
        }
    }

    private static Set<String> reachable(String start, Map<ScopedStatement, Set<ScopedStatement>> edges) {
        LinkedHashSet<ScopedStatement> visited = new LinkedHashSet<>();
        Deque<ScopedStatement> queue = new ArrayDeque<>();
        edges.keySet().stream().filter(key -> key.id().equals(start)).forEach(key -> queue.addAll(edges.get(key)));
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            ScopedStatement next = queue.removeFirst();
            if (!visited.add(next)) {
                continue;
            }
            if (!Objects.equals(next.id(), start)) {
                ids.add(next.id());
            }
            queue.addAll(edges.getOrDefault(next, Set.of()));
        }
        return ids;
    }

    private static Map<ScopedStatement, Set<ScopedStatement>> reverse(
            Map<ScopedStatement, Set<ScopedStatement>> dependencies) {
        LinkedHashMap<ScopedStatement, Set<ScopedStatement>> reversed = new LinkedHashMap<>();
        dependencies.keySet().forEach(id -> reversed.put(id, new LinkedHashSet<>()));
        for (Map.Entry<ScopedStatement, Set<ScopedStatement>> entry : dependencies.entrySet()) {
            for (ScopedStatement target : entry.getValue()) {
                reversed.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        return reversed;
    }
}
