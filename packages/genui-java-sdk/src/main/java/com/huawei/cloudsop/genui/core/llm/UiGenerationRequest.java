/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI生成请求，记录扩展标识、用户输入、请求/响应数据模型等生成参数。
 *
 * <p>{@link #ext}为宿主服务扩展入参，用于传递模板直出等场景所需的额外字段 （如iframeUrl、title、templateId、renderPiu、scenario等），
 * SDK核心LLM链路不读取此字段，仅由模板解析器使用。
 *
 * <p>{@link #renderComponentsKey}为渲染组件标识，当前仅支持 {@code "Markdown"}。 当值为 {@code "Markdown"} 时，走
 * markdown 渲染路径， 让模型直接生成 markdown 内容并包裹为 {@code root = Stack([MarkDownRenderer(...)])}。
 *
 * <p>{@code includeBasic} 与 {@code specifiedComponents} 仅为宿主兼容而保留。本 SDK 的 DSL 生成
 * 不实现组件过滤,遇到显式过滤值会抛异常拒绝。
 *
 * @param extensionId 扩展标识,对应宿主注册的扩展;可为 null
 * @param userInput 用户输入文本
 * @param request 请求上下文数据,序列化后作为 Request Context 拼入用户消息
 * @param response 响应数据模型,作为 Render Data Model 使用,并在结果中原样回显
 * @param suggestion 额外生成指令;空白时忽略
 * @param locale 语言标识(如 zh/en)
 * @param editMode 是否编辑模式
 * @param inlineMode 是否内联模式
 * @param toolCalls 是否启用工具调用
 * @param bindings 是否启用数据绑定
 * @param renderComponentsKey 渲染组件标识;当前仅识别 {@code "Markdown"}
 * @param ext 宿主扩展入参,SDK 核心 LLM 链路不读取
 * @param includeBasic 保留的组件过滤设置;本版本 DSL 生成拒绝显式值
 * @param specifiedComponents 保留的组件白名单;非空时 DSL 生成拒绝
 * @since 2026-06-23
 */
public record UiGenerationRequest(
        String extensionId,
        String userInput,
        Map<String, Object> request,
        Map<String, Object> response,
        String suggestion,
        String locale,
        Boolean editMode,
        Boolean inlineMode,
        Boolean toolCalls,
        Boolean bindings,
        String renderComponentsKey,
        Map<String, Object> ext,
        Boolean includeBasic,
        List<String> specifiedComponents) {
    /** 规范化各组件的存储形式:map 字段的 null 视为空,并复制为保持插入顺序的不可变副本; 组件列表的 null 视为空列表。 */
    public UiGenerationRequest {
        request = immutableOrderedMap(request);
        response = immutableOrderedMap(response);
        ext = immutableOrderedMap(ext);
        specifiedComponents =
                specifiedComponents == null ? List.of() : List.copyOf(specifiedComponents);
    }

    private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
        return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    /**
     * 向后兼容构造函数，不传组件过滤参数，行为与之前一致（全量组件）。
     *
     * @param extensionId 扩展标识
     * @param userInput 用户输入
     * @param request 请求上下文数据
     * @param response 响应数据结构
     * @param suggestion 建议内容
     * @param locale 语言标识
     * @param editMode 编辑模式
     * @param inlineMode 内联模式
     * @param toolCalls 工具调用
     * @param bindings 绑定
     * @param renderComponentsKey 渲染组件标识
     * @param ext 扩展入参
     */
    public UiGenerationRequest(
            String extensionId,
            String userInput,
            Map<String, Object> request,
            Map<String, Object> response,
            String suggestion,
            String locale,
            Boolean editMode,
            Boolean inlineMode,
            Boolean toolCalls,
            Boolean bindings,
            String renderComponentsKey,
            Map<String, Object> ext) {
        this(
                extensionId,
                userInput,
                request,
                response,
                suggestion,
                locale,
                editMode,
                inlineMode,
                toolCalls,
                bindings,
                renderComponentsKey,
                ext,
                null,
                null);
    }

    /**
     * 创建请求构建器。
     *
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 保留原始意图、数据与宿主扩展,构造一个走 Markdown 渲染路径的请求副本。
     *
     * @return {@code renderComponentsKey} 为 {@code "Markdown"} 的新请求
     */
    public UiGenerationRequest asMarkdown() {
        return new UiGenerationRequest(
                extensionId,
                userInput,
                request,
                response,
                suggestion,
                locale,
                editMode,
                inlineMode,
                toolCalls,
                bindings,
                "Markdown",
                ext,
                includeBasic,
                specifiedComponents);
    }

    /**
     * 兼容旧调用方的十字段构造函数,渲染组件标识与扩展入参留空,组件过滤取默认值。
     *
     * @param extensionId 扩展标识
     * @param userInput 用户输入
     * @param request 请求上下文数据
     * @param response 响应数据结构
     * @param suggestion 建议内容
     * @param locale 语言标识
     * @param editMode 编辑模式
     * @param inlineMode 内联模式
     * @param toolCalls 工具调用
     * @param bindings 绑定
     */
    public UiGenerationRequest(
            String extensionId,
            String userInput,
            Map<String, Object> request,
            Map<String, Object> response,
            String suggestion,
            String locale,
            Boolean editMode,
            Boolean inlineMode,
            Boolean toolCalls,
            Boolean bindings) {
        this(
                extensionId,
                userInput,
                request,
                response,
                suggestion,
                locale,
                editMode,
                inlineMode,
                toolCalls,
                bindings,
                null,
                null,
                null,
                null);
    }

    /**
     * UI生成请求构建器。
     *
     * @since 2026
     */
    public static final class Builder {
        private String extensionId;

        private String userInput;

        private Map<String, Object> request;

        private Map<String, Object> response;

        private String suggestion;

        private String locale;

        private Boolean editMode;

        private Boolean inlineMode;

        private Boolean toolCalls;

        private Boolean bindings;

        private String renderComponentsKey;

        private Map<String, Object> ext;

        /** API名称列表，仅存储由宿主服务传入的值，SDK核心LLM链路不使用此字段。 */
        private List<String> apiNameList;

        private Boolean includeBasic;

        private List<String> specifiedComponents;

        private Builder() {}

        /**
         * 设置扩展标识。
         *
         * @param extensionId 扩展ID
         * @return 当前构建器
         */
        public Builder extensionId(String extensionId) {
            this.extensionId = extensionId;
            return this;
        }

        /**
         * 设置用户输入文本。
         *
         * @param userInput 用户输入
         * @return 当前构建器
         */
        public Builder userInput(String userInput) {
            this.userInput = userInput;
            return this;
        }

        /**
         * 设置请求数据模型。
         *
         * @param request 请求上下文数据
         * @return 当前构建器
         */
        public Builder request(Map<String, Object> request) {
            this.request = request;
            return this;
        }

        /**
         * 设置响应数据模型。
         *
         * @param response 响应数据结构
         * @return 当前构建器
         */
        public Builder response(Map<String, Object> response) {
            this.response = response;
            return this;
        }

        /**
         * 设置生成建议。
         *
         * @param suggestion 建议内容
         * @return 当前构建器
         */
        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        /**
         * 设置国际化语言标识。
         *
         * @param locale 语言标识（zh/en）
         * @return 当前构建器
         */
        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        /**
         * 设置是否为编辑模式。
         *
         * @param editMode 是否编辑模式
         * @return 当前构建器
         */
        public Builder editMode(Boolean editMode) {
            this.editMode = editMode;
            return this;
        }

        /**
         * 设置是否为内联模式。
         *
         * @param inlineMode 是否内联模式
         * @return 当前构建器
         */
        public Builder inlineMode(Boolean inlineMode) {
            this.inlineMode = inlineMode;
            return this;
        }

        /**
         * 设置是否启用工具调用。
         *
         * @param toolCalls 是否启用
         * @return 当前构建器
         */
        public Builder toolCalls(Boolean toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        /**
         * 设置是否启用数据绑定。
         *
         * @param bindings 是否启用
         * @return 当前构建器
         */
        public Builder bindings(Boolean bindings) {
            this.bindings = bindings;
            return this;
        }

        /**
         * 设置渲染组件标识。当值为 "Markdown" 时，走 markdown 渲染路径， 让模型直接生成 markdown 内容并包裹为 MarkDownRenderer。
         *
         * @param renderComponentsKey 渲染组件标识
         * @return 当前构建器
         */
        public Builder renderComponentsKey(String renderComponentsKey) {
            this.renderComponentsKey = renderComponentsKey;
            return this;
        }

        /**
         * 设置宿主服务扩展入参。用于传递模板直出等场景所需的额外字段 （如iframeUrl、title、templateId、renderPiu、scenario等），
         * SDK核心LLM链路不读取此字段。
         *
         * @param ext 扩展入参映射
         * @return 当前构建器
         */
        public Builder ext(Map<String, Object> ext) {
            this.ext = ext;
            return this;
        }

        /**
         * 设置API名称列表。仅保存宿主服务传入的值，SDK核心LLM链路不读取此字段。
         *
         * @param apiNameList API名称列表
         * @return 当前构建器
         */
        public Builder apiNameList(List<String> apiNameList) {
            this.apiNameList = apiNameList;
            return this;
        }

        /**
         * 保留的组件过滤设置;本版本的 DSL 生成不支持组件过滤,显式值会在生成时被拒绝。
         *
         * @param includeBasic 是否包含基础组件
         * @return 当前构建器
         */
        public Builder includeBasic(Boolean includeBasic) {
            this.includeBasic = includeBasic;
            return this;
        }

        /**
         * 保留的组件过滤设置;本版本的 DSL 生成不支持组件过滤,非空列表会在生成时被拒绝。
         *
         * @param specifiedComponents 指定组件名列表
         * @return 当前构建器
         */
        public Builder specifiedComponents(List<String> specifiedComponents) {
            this.specifiedComponents = specifiedComponents;
            return this;
        }

        /**
         * 构建请求实例。
         *
         * @return UiGenerationRequest实例
         */
        public UiGenerationRequest build() {
            Map<String, Object> effectiveExt = ext;
            if (apiNameList != null) {
                effectiveExt = new LinkedHashMap<>(ext == null ? Map.of() : ext);
                effectiveExt.put("apiNameList", List.copyOf(apiNameList));
            }
            return new UiGenerationRequest(
                    extensionId,
                    userInput,
                    request,
                    response,
                    suggestion,
                    locale,
                    editMode,
                    inlineMode,
                    toolCalls,
                    bindings,
                    renderComponentsKey,
                    effectiveExt,
                    includeBasic,
                    specifiedComponents);
        }
    }
}
