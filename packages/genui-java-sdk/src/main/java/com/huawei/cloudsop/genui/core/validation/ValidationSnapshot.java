/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
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
 * Internal validation input. Only the validation pipeline can build a complete, consistent
 * snapshot.
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
        builtins =
                BuiltinCatalog.of(
                        copyBuiltins(
                                BuiltinCatalog.fromContract(contract).specsForTypeValidation()));
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

    public Program program() {
        return program;
    }

    public ContractCatalog catalog() {
        return catalog;
    }

    public BuiltinCatalog builtins() {
        return builtins;
    }

    public ProgramAnalysis analysis() {
        return analysis;
    }

    public Map<String, Object> dataModel() {
        return dataModel;
    }

    public String rootName() {
        return rootName;
    }

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
                copyBuiltins(source.builtins()),
                source.capabilityGaps());
    }

    private static List<BuiltinSpec> copyBuiltins(Iterable<BuiltinSpec> source) {
        List<BuiltinSpec> builtins = new ArrayList<>();
        for (BuiltinSpec spec : source) {
            List<BuiltinSpec.ParamSpec> params =
                    spec.params().stream()
                            .map(
                                    param ->
                                            new BuiltinSpec.ParamSpec(
                                                    param.name(),
                                                    param.type(),
                                                    copyType(param.typeExpr()),
                                                    param.optional(),
                                                    param.variadic(),
                                                    param.binder(),
                                                    param.nameSyntax()))
                            .toList();
            // 结构化类型规则与 binder 布局随词表一并进入快照（#81/#83）：同一次契约解析，不在快照层剥离。
            builtins.add(
                    new BuiltinSpec(
                            spec.name(),
                            spec.signature(),
                            spec.description(),
                            spec.templateBuiltin(),
                            params,
                            copyType(spec.returnType()),
                            spec.typeRule(),
                            spec.inputIndex(),
                            spec.callForms()));
        }
        return List.copyOf(builtins);
    }

    private static TypeExpr copyType(TypeExpr type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TypeExpr.ArrayOf array -> new TypeExpr.ArrayOf(copyType(array.element()));
            case TypeExpr.RecordOf record -> new TypeExpr.RecordOf(copyType(record.value()));
            case TypeExpr.ObjectLiteral object ->
                    new TypeExpr.ObjectLiteral(
                            object.fields().stream()
                                    .map(
                                            field ->
                                                    new TypeExpr.ObjectLiteral.Field(
                                                            field.name(), copyType(field.type())))
                                    .toList());
            case TypeExpr.UnionOf union ->
                    new TypeExpr.UnionOf(
                            union.alternatives().stream()
                                    .map(ValidationSnapshot::copyType)
                                    .toList());
            case TypeExpr.Conditional conditional ->
                    new TypeExpr.Conditional(
                            conditional.paramIndex(),
                            copyType(conditional.whenArray()),
                            copyType(conditional.otherwise()));
            default -> type;
        };
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
