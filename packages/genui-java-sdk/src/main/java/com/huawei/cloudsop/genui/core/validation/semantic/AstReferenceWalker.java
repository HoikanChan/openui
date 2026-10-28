/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 表达式语句引用遍历的唯一实现:统一 AST 子节点递归与惰性 builtin 的 binder 遮蔽,不决定“哪个名字算语句引用”。
 *
 * <p>
 * 两个调用方原有的遍历规则不同,这里显式保留为两个入口,不互相靠拢:
 * <ul>
 * <li>{@link #walkReachability}:类型分析的入口可达收集——binder 位实参照常在外层作用域遍历,非法 binder 名跳过但有效名仍建立作用域,
 * {@code Assign} 的值不遍历;</li>
 * <li>{@link #walkDependencies}:语句依赖边收集——binder 位实参不是表达式引用,任一 binder 名非法则整个调用不建立作用域,
 * {@code Assign} 的值参与遍历。</li>
 * </ul>
 *
 * <p>
 * 共有部分只有节点分派、作用域叠加和 body 位切换;布局全部来自 {@link LazyBuiltinBinders} 的契约解析。调用方通过 {@link RefSink}
 * 决定收不收某个未被遮蔽的引用。
 *
 * @since 2026
 */
public final class AstReferenceWalker {

    /** 未被 binder 遮蔽的 {@code Ref};{@code scope} 是当时已绑定的名字集合。 */
    @FunctionalInterface
    public interface RefSink {
        void accept(String name, Set<String> scope);
    }

    /**
     * 遍历规则差异:是否跳过 binder 位实参、是否要求 binder 名全合法、是否遍历赋值右侧。
     */
    private record Policy(boolean skipBinderArguments, boolean requireAllBindersValid, boolean traverseAssignmentValues) {
    }

    /** 类型分析可达收集的既有规则。 */
    private static final Policy REACHABILITY = new Policy(false, false, false);

    /** 语句依赖收集的既有规则。 */
    private static final Policy DEPENDENCIES = new Policy(true, true, true);

    private AstReferenceWalker() {
    }

    /** 类型分析可达收集:从空作用域遍历一个表达式。 */
    public static void walkReachability(AstNode node, BuiltinCatalog builtins, RefSink sink) {
        walk(node, builtins, Set.of(), sink, REACHABILITY, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /** 语句依赖收集:在命名模板的使用位置绑定下遍历表达式。 */
    public static void walkDependencies(AstNode node, BuiltinCatalog builtins, Set<String> initialScope, RefSink sink) {
        walk(node, builtins, Set.copyOf(initialScope), sink, DEPENDENCIES,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void walk(AstNode node, BuiltinCatalog builtins, Set<String> scope, RefSink sink, Policy policy,
            Set<AstNode> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        if (node instanceof AstNode.Ref ref) {
            if (!scope.contains(ref.n())) {
                sink.accept(ref.n(), scope);
            }
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            walkCall(comp, builtins, scope, sink, policy, visited);
            return;
        }
        if (node instanceof AstNode.Assign assign) {
            if (policy.traverseAssignmentValues()) {
                walk(assign.value(), builtins, scope, sink, policy, visited);
            }
            return;
        }
        for (AstNode child : children(node)) {
            walk(child, builtins, scope, sink, policy, visited);
        }
    }

    /** 惰性 builtin 的 binder 只作用于 body;按各调用方既有规则决定 binder 实参与非法名字的处理。 */
    private static void walkCall(AstNode.Comp comp, BuiltinCatalog builtins, Set<String> scope, RefSink sink,
            Policy policy, Set<AstNode> visited) {
        List<Integer> binderIndexes = LazyBuiltinBinders.binderIndexes(comp, builtins);
        if (!binderIndexes.isEmpty()) {
            BuiltinSpec spec = builtins.spec(comp.name());
            int bodyIndex = spec == null ? -1 : LazyBuiltinBinders.bodyIndex(spec, comp);
            Set<String> binders = new LinkedHashSet<>();
            boolean valid = spec != null && bodyIndex >= 0 && bodyIndex < comp.args().size();
            if (valid) {
                for (int index : binderIndexes) {
                    String binder = LazyBuiltinBinders.binderName(spec, comp, index);
                    if (binder == null) {
                        if (policy.requireAllBindersValid()) {
                            valid = false;
                            break;
                        }
                        continue;
                    }
                    binders.add(binder);
                }
            }
            if (valid) {
                for (int index = 0; index < comp.args().size(); index++) {
                    if (index == bodyIndex || (policy.skipBinderArguments() && binderIndexes.contains(index))) {
                        continue;
                    }
                    walk(comp.args().get(index), builtins, scope, sink, policy, visited);
                }
                Set<String> bodyScope = new LinkedHashSet<>(scope);
                bodyScope.addAll(binders);
                walk(comp.args().get(bodyIndex), builtins, bodyScope, sink, policy, visited);
                return;
            }
        }
        for (AstNode argument : comp.args()) {
            walk(argument, builtins, scope, sink, policy, visited);
        }
    }

    private static List<AstNode> children(AstNode node) {
        return switch (node) {
            case AstNode.Arr array -> array.els();
            case AstNode.Obj object -> object.entries().stream().map(AstNode.Obj.Entry::value).toList();
            case AstNode.BinOp binary -> List.of(binary.left(), binary.right());
            case AstNode.UnaryOp unary -> List.of(unary.operand());
            case AstNode.Ternary ternary -> List.of(ternary.cond(), ternary.then(), ternary.otherwise());
            case AstNode.Member member -> List.of(member.obj());
            case AstNode.Index index -> List.of(index.obj(), index.index());
            default -> List.of();
        };
    }
}
