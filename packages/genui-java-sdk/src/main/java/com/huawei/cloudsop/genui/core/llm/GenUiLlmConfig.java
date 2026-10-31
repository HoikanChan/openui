/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 调用配置:端点、模型、采样参数与额外请求头。实例不可变,由 {@link Builder} 构建。
 *
 * @since 2026
 */
public final class GenUiLlmConfig {
    /** 默认 chat completions 端点。 */
    public static final String DEFAULT_ENDPOINT = "/rest/netrsn/v1/chat/completions";

    /** 默认模型名。 */
    public static final String DEFAULT_MODEL = "qwen3.6-27b";

    private final String endpoint;
    private final String defaultModel;
    private final double temperature;
    private final boolean enableThinking;
    private final boolean jsonObjectResponse;
    private final Map<String, String> extraHeaders;

    private GenUiLlmConfig(Builder builder) {
        this.endpoint = defaultIfBlank(builder.endpoint, DEFAULT_ENDPOINT);
        this.defaultModel = defaultIfBlank(builder.defaultModel, DEFAULT_MODEL);
        this.temperature = builder.temperature;
        this.enableThinking = builder.enableThinking;
        this.jsonObjectResponse = builder.jsonObjectResponse;
        this.extraHeaders = Map.copyOf(builder.extraHeaders);
    }

    /**
     * 返回全部取默认值的配置:默认端点、默认模型、温度 0、关闭思考与 JSON object 响应。
     *
     * @return 默认配置实例
     */
    public static GenUiLlmConfig defaults() {
        return builder().build();
    }

    /**
     * 创建配置构建器。
     *
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回生效的 chat completions 端点,空白配置已回退为默认值。
     *
     * @return 相对路径或绝对 URL
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * 返回生效的模型名,空白配置已回退为默认值。
     *
     * @return 模型名
     */
    public String defaultModel() {
        return defaultModel;
    }

    /**
     * 返回采样温度。
     *
     * @return 采样温度
     */
    public double temperature() {
        return temperature;
    }

    /**
     * 是否开启模型思考输出。
     *
     * @return 开启思考时为 true
     */
    public boolean enableThinking() {
        return enableThinking;
    }

    /**
     * 是否要求模型返回 JSON object。
     *
     * @return 需要 JSON object 响应时为 true
     */
    public boolean jsonObjectResponse() {
        return jsonObjectResponse;
    }

    /**
     * 返回额外请求头,不可变 map。
     *
     * @return 额外请求头
     */
    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 配置构建器,可变且非线程安全;由 {@link #builder()} 创建。
     *
     * @since 2026
     */
    public static final class Builder {
        private String endpoint;
        private String defaultModel;
        private double temperature;
        private boolean enableThinking;
        private boolean jsonObjectResponse;
        private final LinkedHashMap<String, String> extraHeaders = new LinkedHashMap<>();

        private Builder() {}

        /**
         * 设置 chat completions 端点;空白值在 {@link #build()} 时回退到默认端点。
         *
         * @param endpoint 相对路径或绝对 URL
         * @return 当前构建器
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * 设置模型名;空白值在 {@link #build()} 时回退到默认模型。
         *
         * @param defaultModel 模型名
         * @return 当前构建器
         */
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /**
         * 设置采样温度,默认 0。
         *
         * @param temperature 采样温度
         * @return 当前构建器
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置是否开启模型思考输出。
         *
         * @param enableThinking 是否开启思考
         * @return 当前构建器
         */
        public Builder enableThinking(boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        /**
         * 设置是否要求模型返回 JSON object。
         *
         * @param jsonObjectResponse 是否请求 JSON object 响应
         * @return 当前构建器
         */
        public Builder jsonObjectResponse(boolean jsonObjectResponse) {
            this.jsonObjectResponse = jsonObjectResponse;
            return this;
        }

        /**
         * 整体替换额外请求头;先清空已有项,{@code extraHeaders} 为 null 时结果为空。
         *
         * @param extraHeaders 要设置的请求头,可为 null
         * @return 当前构建器
         */
        public Builder extraHeaders(Map<String, String> extraHeaders) {
            this.extraHeaders.clear();
            if (extraHeaders != null) {
                extraHeaders.forEach(this::putHeader);
            }
            return this;
        }

        /**
         * 追加一个请求头;名或值为 null 时忽略。
         *
         * @param name 请求头名
         * @param value 请求头值
         * @return 当前构建器
         */
        public Builder putHeader(String name, String value) {
            if (name != null && value != null) {
                this.extraHeaders.put(name, value);
            }
            return this;
        }

        /**
         * 构建配置实例;endpoint 与模型名为空白时回退到默认值,额外请求头复制为不可变 map。
         *
         * @return 新的不可变配置实例
         */
        public GenUiLlmConfig build() {
            return new GenUiLlmConfig(this);
        }
    }
}
