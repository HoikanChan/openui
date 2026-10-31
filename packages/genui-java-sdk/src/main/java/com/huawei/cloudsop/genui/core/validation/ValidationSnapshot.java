/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalysis;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验管线的内部输入：只由校验流程构造完整且自洽的快照。
 *
 * @since 2026
 */
public final class ValidationSnapshot {
    private final Program program;
    private final ContractCatalog catalog;
    private final BuiltinCatalog builtins;
    private final ProgramAnalysis analysis;
    private final Map<String, Object> dataModel;
    private final String rootName;
    private final Set<String> externalRefs;
    private final String normalizedDsl;

    private ValidationSnapshot(ValidationRequest request) {
        GenerationContract contract = copyContract(request.contract());
        dataModel = request.dataModel();
        // 简洁定义一经加载即不可变(BuiltinSpec/ParamSpec/TypeExpr 复制构造),直接派生词表:
        // 不再"取出规则再复制一遍、重建第二次 catalog",也不牺牲快照隔离。
        builtins = BuiltinCatalog.fromContract(contract);
        catalog = ContractCatalog.from(contract);
        String dsl = request.dsl() == null ? "" : request.dsl();
        OpenuiPreprocessor.Result preprocessed = OpenuiPreprocessor.process(dsl, ParseMode.FINAL);
        program = OpenuiParser.parse(preprocessed, builtins);
        rootName = request.rootName();
        LinkedHashSet<String> refs = new LinkedHashSet<>(request.externalRefs());
        if (dataModel != null) {
            refs.add("data");
        }
        externalRefs = Collections.unmodifiableSet(refs);
        analysis = new ProgramAnalyzer(catalog, builtins).analyze(program, rootName, externalRefs);
        normalizedDsl = preprocessed.text();
    }

    static ValidationSnapshot build(ValidationRequest request) {
        return new ValidationSnapshot(request);
    }

    /**
     * 预处理并解析后的程序。
     *
     * @return 语法分析产物
     */
    public Program program() {
        return program;
    }

    /**
     * 组件契约目录。
     *
     * @return 从请求契约派生的组件目录
     */
    public ContractCatalog catalog() {
        return catalog;
    }

    /**
     * 本次校验使用的 builtin 词表。
     *
     * @return 从请求契约派生的词表
     */
    public BuiltinCatalog builtins() {
        return builtins;
    }

    /**
     * 语义分析结果（含问题与入口语句）。
     *
     * @return 程序分析产物
     */
    public ProgramAnalysis analysis() {
        return analysis;
    }

    /**
     * 渲染数据模型。
     *
     * @return 请求提供的数据模型；未提供时为 {@code null}
     */
    public Map<String, Object> dataModel() {
        return dataModel;
    }

    /**
     * 根组件名。
     *
     * @return 请求指定的根组件名；未指定时为 {@code null}
     */
    public String rootName() {
        return rootName;
    }

    /**
     * 外部引用名集合。
     *
     * @return 外部引用名；提供数据模型时包含 {@code data}
     */
    public Set<String> externalRefs() {
        return externalRefs;
    }

    String normalizedDsl() {
        return normalizedDsl;
    }

    private static GenerationContract copyContract(GenerationContract source) {
        if (source == null) {
            return null;
        }
        Map<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        source.components()
                .forEach(
                        (name, spec) ->
                                components.put(
                                        name,
                                        new ComponentPromptSpec(
                                                spec.description(), copyMap(spec.propsSchema()))));
        // Only validation-consumed contract data is retained. Prompt-only tools/groups are not
        // analysis inputs.
        return new GenerationContract(
                source.contractVersion(),
                source.root(),
                components,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                source.builtins(),
                source.capabilityGaps());
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(key, copyValue(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(copyValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
