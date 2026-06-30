/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GenUiLlmConfig {
    public static final String DEFAULT_ENDPOINT = "/rest/netrsn/v1/chat/completions";
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

    public static GenUiLlmConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String endpoint() {
        return endpoint;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public double temperature() {
        return temperature;
    }

    public boolean enableThinking() {
        return enableThinking;
    }

    public boolean jsonObjectResponse() {
        return jsonObjectResponse;
    }

    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static final class Builder {
        private String endpoint;
        private String defaultModel;
        private double temperature;
        private boolean enableThinking;
        private boolean jsonObjectResponse;
        private final LinkedHashMap<String, String> extraHeaders = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder enableThinking(boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }

        public Builder jsonObjectResponse(boolean jsonObjectResponse) {
            this.jsonObjectResponse = jsonObjectResponse;
            return this;
        }

        public Builder extraHeaders(Map<String, String> extraHeaders) {
            this.extraHeaders.clear();
            if (extraHeaders != null) {
                extraHeaders.forEach(this::putHeader);
            }
            return this;
        }

        public Builder putHeader(String name, String value) {
            if (name != null && value != null) {
                this.extraHeaders.put(name, value);
            }
            return this;
        }

        public GenUiLlmConfig build() {
            return new GenUiLlmConfig(this);
        }
    }
}
