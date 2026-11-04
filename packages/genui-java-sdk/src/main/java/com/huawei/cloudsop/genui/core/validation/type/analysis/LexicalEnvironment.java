/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 不可变父子链词法环境;binder 只影响声明的 body,嵌套同名正常遮蔽。
 *
 * @since 2026
 */
final class LexicalEnvironment {
    private final LexicalEnvironment parent;
    private final String name;
    private final Fact value;

    private LexicalEnvironment(LexicalEnvironment parent, String name, Fact value) {
        this.parent = parent;
        this.name = name;
        this.value = value;
    }

    static LexicalEnvironment root() {
        return new LexicalEnvironment(null, null, null);
    }

    LexicalEnvironment bind(String boundName, Fact boundValue) {
        return new LexicalEnvironment(this, boundName, boundValue);
    }

    Optional<Fact> lookup(String lookedUp) {
        for (LexicalEnvironment env = this; env != null; env = env.parent) {
            if (env.name != null && env.name.equals(lookedUp)) {
                return Optional.ofNullable(env.value);
            }
        }
        return Optional.empty();
    }

    boolean isBound(String lookedUp) {
        for (LexicalEnvironment env = this; env != null; env = env.parent) {
            if (env.name != null && env.name.equals(lookedUp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把本环境(更内层、优先)的绑定叠加到 {@code base} 上,用于在使用位置保留声明环境并继承使用位置绑定 (ADR 0006):同名以本环境为准,退出后自然回到 {@code
     * base} 的绑定。
     *
     * @param base 被叠加的基础环境;null 时从根环境开始
     * @return 叠加当前绑定后的新环境
     */
    LexicalEnvironment over(LexicalEnvironment base) {
        List<LexicalEnvironment> chain = new ArrayList<>();
        for (LexicalEnvironment env = this; env != null && env.name != null; env = env.parent) {
            chain.add(env);
        }
        LexicalEnvironment result = base == null ? root() : base;
        for (int index = chain.size() - 1; index >= 0; index--) {
            LexicalEnvironment node = chain.get(index);
            result = result.bind(node.name, node.value);
        }
        return result;
    }
}
