/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UI 生成请求,记录扩展标识、用户输入、请求/响应数据模型等生成参数。
 *
 * <p>
 * {@code ext} 为宿主服务扩展入参(如 {@code ext.scenario}),用于传递场景化渲染所需的额外字段;SDK 核心 LLM 链路 不消费此字段,仅由 {@code MarkdownRenderer}
 * 等场景包裹器读取。master 上该包裹器先于完整 #57 请求面落地,故此处仅补 {@code ext} 单字段。
 */
public record UiGenerationRequest(String extensionId, String userInput, Map<String, Object> request,
        Map<String, Object> response, String suggestion, Boolean editMode, Boolean inlineMode, Boolean toolCalls,
        Boolean bindings, Map<String, Object> ext) {
    public UiGenerationRequest {
        request = immutableOrderedMap(request);
        response = immutableOrderedMap(response);
        ext = immutableOrderedMap(ext);
    }

    /**
     * 源兼容构造器:不带 ext 的旧签名(等价于 {@code ext = null})。
     */
    public UiGenerationRequest(String extensionId, String userInput, Map<String, Object> request,
            Map<String, Object> response, String suggestion, Boolean editMode, Boolean inlineMode, Boolean toolCalls,
            Boolean bindings) {
        this(extensionId, userInput, request, response, suggestion, editMode, inlineMode, toolCalls, bindings, null);
    }

    private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String extensionId;
        private String userInput;
        private Map<String, Object> request;
        private Map<String, Object> response;
        private String suggestion;
        private Boolean editMode;
        private Boolean inlineMode;
        private Boolean toolCalls;
        private Boolean bindings;
        private Map<String, Object> ext;

        private Builder() {
        }

        public Builder extensionId(String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        public Builder userInput(String userInput) {
            this.userInput = userInput;
            return this;
        }

        public Builder request(Map<String, Object> request) {
            this.request = request;
            return this;
        }

        public Builder response(Map<String, Object> response) {
            this.response = response;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder editMode(Boolean editMode) {
            this.editMode = editMode;
            return this;
        }

        public Builder inlineMode(Boolean inlineMode) {
            this.inlineMode = inlineMode;
            return this;
        }

        public Builder toolCalls(Boolean toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder bindings(Boolean bindings) {
            this.bindings = bindings;
            return this;
        }

        public Builder ext(Map<String, Object> ext) {
            this.ext = ext;
            return this;
        }

        public UiGenerationRequest build() {
            return new UiGenerationRequest(extensionId, userInput, request, response, suggestion, editMode, inlineMode,
                    toolCalls, bindings, ext);
        }
    }
}
