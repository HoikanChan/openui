/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * DSL 校验器的输入。
 *
 * <p>
 * 请使用 {@link #builder()} 构造实例。{@code externalRefs} 集合始终非空且不可变；传入 {@code null} 会得到空集合。
 *
 * @param dsl
 *            待校验的 DSL 字符串
 * @param contract
 *            用于校验的合约；{@code null} 表示仅做语法校验
 * @param rootName
 *            预期的根组件名，或 {@code null}
 * @param externalRefs
 *            DSL 中引用的外部类型名集合；不会为 {@code null}
 * @param mode
 *            校验模式
 * @param requestId
 *            调用方提供的日志关联 id，或 {@code null}
 *
 * @since 2026
 */
public record ValidationRequest(String dsl, GenerationContract contract, String rootName, Set<String> externalRefs,
        ValidationMode mode, String requestId) {

    /**
     * 紧凑构造方法，将 {@code externalRefs} 归一化为不可变集合。
     */
    public ValidationRequest {
        externalRefs = externalRefs == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(externalRefs));
    }

    /**
     * 创建一个新的构造器。
     *
     * @return 构造器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link ValidationRequest} 的构造器。
     *
     * @since 2026
     */
    public static final class Builder {
        private String dsl;
        private GenerationContract contract;
        private String rootName;
        private Set<String> externalRefs;
        private ValidationMode mode;
        private String requestId;

        private Builder() {
        }

        /**
         * 设置待校验的 DSL 字符串。
         *
         * @param dsl
         *            DSL 字符串
         * @return 本构造器，便于链式调用
         */
        public Builder dsl(String dsl) {
            this.dsl = dsl;
            return this;
        }

        /**
         * 设置用于校验的合约。
         *
         * @param contract
         *            合约，{@code null} 表示仅做语法校验
         * @return 本构造器，便于链式调用
         */
        public Builder contract(GenerationContract contract) {
            this.contract = contract;
            return this;
        }

        /**
         * 设置预期的根组件名。
         *
         * @param rootName
         *            根组件名
         * @return 本构造器，便于链式调用
         */
        public Builder rootName(String rootName) {
            this.rootName = rootName;
            return this;
        }

        /**
         * 设置 DSL 中引用的外部类型名集合。
         *
         * @param externalRefs
         *            外部类型名集合
         * @return 本构造器，便于链式调用
         */
        public Builder externalRefs(Set<String> externalRefs) {
            this.externalRefs = externalRefs;
            return this;
        }

        /**
         * 设置校验模式。
         *
         * @param mode
         *            校验模式
         * @return 本构造器，便于链式调用
         */
        public Builder mode(ValidationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * 设置日志关联 id。
         *
         * @param requestId
         *            关联 id
         * @return 本构造器，便于链式调用
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * 构建 {@link ValidationRequest} 实例。
         *
         * @return 校验请求
         */
        public ValidationRequest build() {
            Objects.requireNonNull(dsl, "dsl");
            Objects.requireNonNull(mode, "mode");
            return new ValidationRequest(dsl, contract, rootName, externalRefs, mode, requestId);
        }
    }
}
