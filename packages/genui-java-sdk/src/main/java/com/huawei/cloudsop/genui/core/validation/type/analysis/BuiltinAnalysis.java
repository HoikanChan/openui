/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * builtin 调用分析的唯一入口(#118 / #117 第 3.2 节)。
 *
 * <p>本 module 拥有函数的全部特殊知识:按名称分派、参数分析时机、普通与特殊结果推导、binder 作用域、
 * 模板捕获与使用位置检查,以及根遍历结束后的未消费模板补查。外层表达式分析只把尚未求值的调用交给 {@link #analyze};语义遍历通过 {@link #walkArguments}
 * 取得实参角色与作用域,不读取模板下标,也不按函数名 分派;表格通过 {@link #checkCellTemplate} 提供 cell 表达式、单元格值与整行值,不自行识别模板构造。
 *
 * <p>无状态的定义能力({@link #validateDefinition}、{@link #walkArguments})供语义遍历与类型分析共用;会话 能力({@link
 * #analyze}、{@link #checkCellTemplate}、{@link #finish})复用 {@link AnalysisSession} 的预算、
 * 循环引用追踪、诊断身份与覆盖统计,不另建会话。
 *
 * <p>特殊函数的布局与语义都在本 module 内按名称固定,契约只声明名称、文案、params 与 returnType: 排序/过滤/选择/展平/对象覆盖与枚举走 {@link
 * TypeOps},Each/Render/Switch 的绑定与模板生命周期也在本类。 契约里不存在的函数不会被这里的逻辑变成合法调用。
 *
 * <p>延迟模板的表达式与捕获环境只保存在本会话内,不进入 {@link Fact},也不暴露跨会话句柄。
 *
 * @since 2026
 */
public final class BuiltinAnalysis {

    /** 逐元素模板构造的名称;布局与作用域由本 module 持有。 */
    private static final String EACH = "Each";

    /** 延迟模板构造的名称;模板在使用位置才检查。 */
    private static final String RENDER = "Render";

    /** 分支结果构造的名称;cases 在当前环境求值。 */
    private static final String SWITCH = "Switch";

    /** 保留数量与元素存在证据的排序。 */
    private static final String SORT = "Sort";

    /** 下界归零并删除行保留证据的过滤。 */
    private static final String FILTER = "Filter";

    /** 选择首元素的数组函数。 */
    private static final String FIRST = "First";

    /** 选择末元素的数组函数。 */
    private static final String LAST = "Last";

    /** 单层展平。 */
    private static final String FLATTEN = "Flatten";

    /** 有序对象覆盖。 */
    private static final String MERGE_OBJECT = "MergeObject";

    /** 对象键枚举。 */
    private static final String OBJECT_KEYS = "ObjectKeys";

    /** 对象值枚举。 */
    private static final String OBJECT_VALUES = "ObjectValues";

    /** 对象键值对枚举。 */
    private static final String OBJECT_ENTRIES = "ObjectEntries";

    /** 按输入形状细化标量/数组结果的日期格式化。 */
    private static final String FORMAT_DATE = "FormatDate";

    /** 有 Java 额外结果语义的普通 builtin;其余函数只走签名推导。 */
    private static final Set<String> DATA_SPECIALS =
            Set.of(
                    SORT,
                    FILTER,
                    FIRST,
                    LAST,
                    FLATTEN,
                    MERGE_OBJECT,
                    OBJECT_KEYS,
                    OBJECT_VALUES,
                    OBJECT_ENTRIES,
                    FORMAT_DATE);

    /** Each/Render 的最长声明参数个数(Each 固定三参;Render 以三参形式声明,中位 row 可选)。 */
    private static final int TEMPLATE_PARAM_COUNT = 3;

    private final BuiltinCatalog catalog;
    private final AnalysisSession session;
    private final Map<AstNode, DeferredTemplate> templates = new IdentityHashMap<>();
    private final List<DeferredTemplate> templateOrder = new ArrayList<>();
    private final Set<AstNode> contextCheckedTemplates =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * 会话能力构造:分析入口与使用位置检查共享本次校验的预算、诊断与覆盖。
     *
     * @param catalog 当前生效的 builtin 词表
     * @param session 承载共享状态的分析会话
     */
    BuiltinAnalysis(BuiltinCatalog catalog, AnalysisSession session) {
        this.catalog = catalog;
        this.session = session;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 内部布局:Each/Render 的参数角色由本 module 固定,契约不再声明
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 一次调用中模板构造的参数角色。
     *
     * @param inputIndex 元素来源实参下标;无元素来源时为 -1
     * @param bodyIndex 模板实参下标
     * @param binderIndexes binder 声明位实参下标
     * @since 2026
     */
    private record Layout(int inputIndex, int bodyIndex, List<Integer> binderIndexes) {}

    /**
     * 该名称是否由本 module 拥有调用布局(Each/Render)。
     *
     * <p>加载器据此跳过普通可选/变长位置规则,不在自身维护函数名单。
     *
     * @param name builtin 名称
     * @return 布局由本 module 固定时为 {@code true}
     */
    public static boolean ownsCallLayout(String name) {
        return EACH.equals(name) || RENDER.equals(name);
    }

    /**
     * 按名称与实参个数取模板布局;不是模板构造或个数不匹配时返回 {@code null}。
     *
     * @param name builtin 名称
     * @param argCount 实际调用实参个数
     * @return 布局;不适用时为 {@code null}
     */
    private static Layout layoutOf(String name, int argCount) {
        if (EACH.equals(name) && argCount == TEMPLATE_PARAM_COUNT) {
            return new Layout(0, 2, List.of(1));
        }
        if (RENDER.equals(name)) {
            // 两参:第一个声明变量,第二个是模板;三参:前两个声明变量,最后一个是模板。
            if (argCount == 2) {
                return new Layout(-1, 1, List.of(0));
            }
            if (argCount == TEMPLATE_PARAM_COUNT) {
                return new Layout(-1, 2, List.of(0, 1));
            }
        }
        return null;
    }

    /**
     * binder 名形态:字符串字面量与裸标识符都接受(与既有执行约定一致),其他形态返回 {@code null}。
     *
     * @param call 被检查的调用
     * @param index binder 实参下标
     * @return binder 名;该实参不是允许的形态时为 {@code null}
     */
    static String binderName(AstNode.Comp call, int index) {
        if (index < 0 || index >= call.args().size()) {
            return null;
        }
        AstNode argument = call.args().get(index);
        if (argument instanceof AstNode.Str str) {
            return str.v();
        }
        if (argument instanceof AstNode.Ref ref) {
            return ref.n();
        }
        return null;
    }

    // ───────────────────────────────────────────────────────────────────────
    // 定义期校验:特殊布局前提与返回声明覆盖
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 定义期结构校验:特殊函数的最低布局前提由本 module 统一检查,加载器不维护函数名单。
     *
     * <p>Each/Render/Switch 的参数角色必须与 Java 实现的固定布局一致;有 Java 额外结果语义的普通函数, 其 returnType
     * 必须覆盖特殊语义可证明的结果上界——声明与处理器冲突时明确失败,不静默裁剪结果掩盖冲突。
     *
     * @param spec 已解析的 builtin 声明
     * @throws GenerationSdkException 布局前提不满足或返回声明可证明冲突时
     */
    public static void validateDefinition(BuiltinSpec spec) {
        switch (spec.name()) {
            case EACH -> validateEach(spec);
            case RENDER -> validateRender(spec);
            case SWITCH -> validateSwitch(spec);
            case SORT,
                            FILTER,
                            FIRST,
                            LAST,
                            FLATTEN,
                            MERGE_OBJECT,
                            OBJECT_KEYS,
                            OBJECT_VALUES,
                            OBJECT_ENTRIES,
                            FORMAT_DATE ->
                    validateSpecialReturn(spec);
            default -> {
                // 普通函数只需通用参数/返回校验。
            }
        }
    }

    private static void validateEach(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        TypeExpr source = paramType(spec, 0);
        if (!(source instanceof TypeExpr.ArrayOf)
                && !(source instanceof TypeExpr.Base base && "array".equals(base.name()))) {
            throw invalid(
                    spec,
                    "Each parameter 0 must be an array of elements, got "
                            + spec.params().get(0).type());
        }
        requireBase(spec, 1, "string", "a binder name");
        requireNoOptionalOrVariadic(spec);
    }

    private static void validateRender(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        requireBase(spec, 0, "string", "a binder name");
        requireBase(spec, 1, "string", "an optional row binder name");
        if (!spec.params().get(1).optional()) {
            throw invalid(spec, "Render parameter 'row' must be optional (2- or 3-argument calls)");
        }
        if (spec.params().get(2).optional() || spec.params().get(0).optional()) {
            throw invalid(spec, "Render parameter 'v' and 'template' must be required");
        }
        if (spec.params().stream().anyMatch(BuiltinSpec.ParamSpec::variadic)) {
            throw invalid(spec, "Render must not declare a variadic parameter");
        }
    }

    private static void validateSwitch(BuiltinSpec spec) {
        requireParamCount(spec, TEMPLATE_PARAM_COUNT);
        if (!(spec.params().get(1).typeExpr() instanceof TypeExpr.RecordOf cases)) {
            throw invalid(spec, "Switch parameter 'cases' must be Record<string, T>");
        }
        if (!spec.params().get(2).optional()) {
            throw invalid(spec, "Switch parameter 'default' must be optional");
        }
        TypeExpr required =
                new TypeExpr.UnionOf(List.of(cases.value(), spec.params().get(2).typeExpr()));
        requireReturnCovers(spec, required);
    }

    /** 普通特殊函数:返回声明必须覆盖 Java 特殊语义可证明的结果上界。 */
    private static void validateSpecialReturn(BuiltinSpec spec) {
        TypeExpr required =
                switch (spec.name()) {
                    case SORT, FILTER -> paramType(spec, 0);
                    case FIRST, LAST -> union(elementOf(paramType(spec, 0)), base("null"));
                    case FLATTEN -> elementOf(paramType(spec, 0));
                    case MERGE_OBJECT -> base("object");
                    case OBJECT_KEYS -> new TypeExpr.ArrayOf(base("string"));
                    case OBJECT_VALUES -> {
                        TypeExpr value = recordValueOf(paramType(spec, 0));
                        yield value == null ? null : new TypeExpr.ArrayOf(value);
                    }
                    case OBJECT_ENTRIES -> {
                        TypeExpr value = recordValueOf(paramType(spec, 0));
                        yield value == null
                                ? null
                                : new TypeExpr.ArrayOf(
                                        new TypeExpr.ObjectLiteral(
                                                List.of(
                                                        new TypeExpr.ObjectLiteral.Field(
                                                                "key", base("string")),
                                                        new TypeExpr.ObjectLiteral.Field(
                                                                "value", value))));
                    }
                    case FORMAT_DATE ->
                            new TypeExpr.UnionOf(
                                    List.of(base("string"), new TypeExpr.ArrayOf(base("string"))));
                    default -> null;
                };
        if (required != null) {
            requireReturnCovers(spec, required);
        }
    }

    private static void requireParamCount(BuiltinSpec spec, int expected) {
        if (spec.params().size() != expected) {
            throw invalid(
                    spec,
                    spec.name()
                            + " requires exactly "
                            + expected
                            + " parameters for its fixed Java layout, got "
                            + spec.params().size());
        }
    }

    private static void requireBase(BuiltinSpec spec, int index, String expected, String role) {
        TypeExpr type = paramType(spec, index);
        if (!(type instanceof TypeExpr.Base base) || !expected.equals(base.name())) {
            throw invalid(
                    spec,
                    spec.name()
                            + " parameter "
                            + index
                            + " must be "
                            + expected
                            + " ("
                            + role
                            + "), got "
                            + spec.params().get(index).type());
        }
    }

    private static void requireNoOptionalOrVariadic(BuiltinSpec spec) {
        for (BuiltinSpec.ParamSpec param : spec.params()) {
            if (param.optional() || param.variadic()) {
                throw invalid(
                        spec,
                        spec.name()
                                + " parameter '"
                                + param.name()
                                + "' must not be optional or variadic");
            }
        }
    }

    private static TypeExpr paramType(BuiltinSpec spec, int index) {
        return spec.params().get(index).typeExpr();
    }

    private static TypeExpr elementOf(TypeExpr type) {
        return type instanceof TypeExpr.ArrayOf array ? array.element() : null;
    }

    private static TypeExpr recordValueOf(TypeExpr type) {
        return type instanceof TypeExpr.RecordOf record ? record.value() : null;
    }

    private static TypeExpr base(String name) {
        return new TypeExpr.Base(name);
    }

    private static TypeExpr union(TypeExpr first, TypeExpr second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new TypeExpr.UnionOf(List.of(first, second));
    }

    private static void requireReturnCovers(BuiltinSpec spec, TypeExpr required) {
        if (spec.returnType() == null) {
            throw invalid(
                    spec,
                    spec.name()
                            + " must declare a returnType covering its Java result ("
                            + required
                            + ")");
        }
        if (!covers(spec.returnType(), required)) {
            throw invalid(
                    spec,
                    spec.name()
                            + " returnType does not cover the Java result shape required by"
                            + " its special semantics");
        }
    }

    /** 声明类型是否覆盖要求类型:union 全分支覆盖、{@code any} 覆盖一切、变量按名匹配。 */
    private static boolean covers(TypeExpr declared, TypeExpr required) {
        if (declared == null || required == null) {
            return false;
        }
        if (required instanceof TypeExpr.UnionOf unionOf) {
            return unionOf.alternatives().stream().allMatch(alt -> covers(declared, alt));
        }
        if (declared instanceof TypeExpr.UnionOf unionOf) {
            return unionOf.alternatives().stream().anyMatch(alt -> covers(alt, required));
        }
        if (declared instanceof TypeExpr.Base base && "any".equals(base.name())) {
            return true;
        }
        return coversSameKind(declared, required);
    }

    private static boolean coversSameKind(TypeExpr declared, TypeExpr required) {
        if (declared instanceof TypeExpr.TypeVar declaredVar
                && required instanceof TypeExpr.TypeVar requiredVar) {
            return declaredVar.name().equals(requiredVar.name());
        }
        if (declared instanceof TypeExpr.ArrayOf declaredArray
                && required instanceof TypeExpr.ArrayOf requiredArray) {
            return covers(declaredArray.element(), requiredArray.element());
        }
        if (declared instanceof TypeExpr.RecordOf declaredRecord
                && required instanceof TypeExpr.RecordOf requiredRecord) {
            return covers(declaredRecord.value(), requiredRecord.value());
        }
        if (declared instanceof TypeExpr.ObjectLiteral declaredObject
                && required instanceof TypeExpr.ObjectLiteral requiredObject) {
            return coversObjectLiteral(declaredObject, requiredObject);
        }
        if (declared instanceof TypeExpr.Base declaredBase
                && required instanceof TypeExpr.Base requiredBase) {
            return declaredBase.name().equals(requiredBase.name());
        }
        return false;
    }

    /** 要求对象的每个字段都必须在声明对象里同名字段且类型被覆盖。 */
    private static boolean coversObjectLiteral(
            TypeExpr.ObjectLiteral declared, TypeExpr.ObjectLiteral required) {
        for (TypeExpr.ObjectLiteral.Field field : required.fields()) {
            boolean covered =
                    declared.fields().stream()
                            .anyMatch(
                                    candidate ->
                                            candidate.name().equals(field.name())
                                                    && covers(candidate.type(), field.type()));
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static GenerationSdkException invalid(BuiltinSpec spec, String message) {
        return new GenerationSdkException("builtin '" + spec.name() + "': " + message);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 无状态遍历能力
    // ───────────────────────────────────────────────────────────────────────

    /**
     * binder 名非法时的遍历策略。两个语义调用方的容错差异是既有契约,由调用方声明而不是在这里合并。
     *
     * @since 2026
     */
    public enum BinderPolicy {
        /** 任一 binder 名非法即退回普通实参遍历(依赖收集、可渲染性)。 */
        REQUIRE_ALL_VALID,
        /** 跳过非法 binder,其余 binder 仍建立作用域(类型分析可达收集)。 */
        SKIP_INVALID
    }

    /**
     * 实参角色回调。普通实参按调用方自己的作用域遍历,模板体按叠加 binder 名后的作用域遍历。
     *
     * @since 2026
     */
    public interface ArgumentVisitor {
        /**
         * 接收一个非模板实参。
         *
         * @param argument 实参表达式
         * @param binderName 合法的 binder 声明位名字;普通实参或非法 binder 为 {@code null}
         */
        void value(AstNode argument, String binderName);

        /**
         * 接收模板/body 实参。
         *
         * @param body 模板表达式
         * @param binderNames 模板体内可见的 binder 名
         */
        void template(AstNode body, Set<String> binderNames);
    }

    /**
     * 按实参角色遍历一次调用:binder 声明位与模板位由本 module 判定,调用方只决定如何遍历。
     *
     * <p>调用不引入 binder 作用域(非模板函数、实参数不足、调用形式不匹配或 binder 名不合法且策略要求 全部合法)时,按普通实参顺序回调;否则先回调非模板实参,最后以
     * binder 作用域回调模板体。
     *
     * @param catalog 当前生效的 builtin 词表
     * @param call 被遍历的调用表达式
     * @param policy binder 名非法时的容错策略
     * @param visitor 接收实参角色与作用域的调用方
     */
    public static void walkArguments(
            BuiltinCatalog catalog,
            AstNode.Comp call,
            BinderPolicy policy,
            ArgumentVisitor visitor) {
        BuiltinSpec spec = catalog.spec(call.name());
        Layout layout = spec == null ? null : layoutOf(call.name(), call.args().size());
        if (layout == null) {
            walkPlain(call, visitor);
            return;
        }
        Set<String> binderNames = new LinkedHashSet<>();
        for (int index : layout.binderIndexes()) {
            String name = binderName(call, index);
            if (name == null) {
                if (policy == BinderPolicy.REQUIRE_ALL_VALID) {
                    walkPlain(call, visitor);
                    return;
                }
                continue;
            }
            binderNames.add(name);
        }
        for (int index = 0; index < call.args().size(); index++) {
            if (index == layout.bodyIndex()) {
                continue;
            }
            String name = layout.binderIndexes().contains(index) ? binderName(call, index) : null;
            visitor.value(call.args().get(index), name);
        }
        visitor.template(call.args().get(layout.bodyIndex()), Set.copyOf(binderNames));
    }

    /** 普通实参遍历:所有实参按调用方自身作用域处理。 */
    private static void walkPlain(AstNode.Comp call, ArgumentVisitor visitor) {
        for (AstNode argument : call.args()) {
            visitor.value(argument, null);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 会话能力:分析、使用位置检查、结束补查
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 一次 builtin 调用的分析上下文:当前语句、词法环境、循环引用追踪,以及模板使用位置的诊断身份。
     *
     * <p>表达式分析、诊断发射与覆盖统计都走原会话,module 不另建会话,也不暴露可任意修改的栈。
     *
     * @since 2026
     */
    static final class AnalysisContext {
        private final AnalysisSession session;
        private final String statementId;
        private final AnalysisSession.LexicalEnvironment env;
        private final Set<String> resolving;
        private final String useSite;
        private final String useSitePath;

        /** 普通调用上下文:无模板使用位置身份。 */
        AnalysisContext(
                AnalysisSession session,
                String statementId,
                AnalysisSession.LexicalEnvironment env,
                Set<String> resolving) {
            this(session, statementId, env, resolving, null, null);
        }

        /** 模板使用位置上下文:带覆盖标签与诊断路径前缀。 */
        AnalysisContext(
                AnalysisSession session,
                String statementId,
                AnalysisSession.LexicalEnvironment env,
                Set<String> resolving,
                String useSite,
                String useSitePath) {
            this.session = session;
            this.statementId = statementId;
            this.env = env;
            this.resolving = resolving;
            this.useSite = useSite;
            this.useSitePath = useSitePath;
        }

        Fact synth(
                AstNode node,
                AnalysisSession.LexicalEnvironment bindings,
                Set<String> resolvingSet) {
            return session.synth(node, statementId, bindings, resolvingSet);
        }

        void emit(String code, String message, String component, String path, String hint) {
            session.emit(code, message, statementId, component, path, hint);
        }

        String statementId() {
            return statementId;
        }

        AnalysisSession.LexicalEnvironment env() {
            return env;
        }

        Set<String> resolving() {
            return resolving;
        }

        String useSite() {
            return useSite;
        }

        String useSitePath() {
            return useSitePath;
        }
    }

    /**
     * 分析一次 builtin 调用:参数分析时机、普通/特殊结果推导与模板捕获都在这里决定。
     *
     * <p>先在契约词表里确认函数存在;不存在的函数不会被任何特殊逻辑变成合法调用。
     *
     * @param call 尚未预先求值的调用表达式
     * @param context 当前语句、词法环境与循环引用追踪
     * @return 调用结果事实
     */
    Fact analyze(AstNode.Comp call, AnalysisContext context) {
        BuiltinSpec spec = catalog.spec(call.name());
        if (spec == null) {
            // 词表命中才会进入本 module;防御性返回未证明,不伪造具体类型。
            return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
        }
        if (SWITCH.equals(call.name())) {
            // cases 的字面量必须逐项保留分支事实,所以由 Switch 自己决定实参分析时机。
            return switchResult(spec, call, context);
        }
        if (ownsCallLayout(call.name())) {
            // 模板构造的参数分析时机由特殊逻辑决定;binder 位与模板位不做普通逐参检查。
            List<Fact> arguments = argumentFacts(spec, call, context, true);
            return switch (call.name()) {
                case EACH -> eachResult(spec, call, arguments, context);
                default -> renderResult(spec, call, context);
            };
        }
        SignatureCall checked = checkSignatureCall(spec, call, context);
        if (DATA_SPECIALS.contains(call.name())) {
            return dataSpecialResult(call, checked.arguments());
        }
        return SignatureInference.instantiate(spec.returnType(), checked.bounds());
    }

    /**
     * 一次调用的实参事实与类型变量绑定。
     *
     * @param arguments 按实参顺序的事实列表
     * @param bounds 本次调用收集到的类型变量绑定
     * @since 2026
     */
    private record SignatureCall(List<Fact> arguments, Map<String, Type> bounds) {}

    /** 逐参求值并按契约 params 检查:数量、位置、可选与变长都由声明决定,特殊结果不再保存另一份参数签名。 */
    private SignatureCall checkSignatureCall(
            BuiltinSpec spec, AstNode.Comp call, AnalysisContext context) {
        checkArity(spec, call, context);
        Map<String, Type> bounds = new LinkedHashMap<>();
        List<Fact> arguments = new ArrayList<>();
        for (int index = 0; index < call.args().size(); index++) {
            Fact actual = context.synth(call.args().get(index), context.env(), context.resolving());
            arguments.add(actual);
            BuiltinSpec.ParamSpec param = paramAt(spec, index);
            if (param == null) {
                continue;
            }
            checkSignatureParam(spec, call, index, param, actual, context);
            SignatureInference.bind(param.typeExpr(), actual, bounds);
        }
        for (int index = call.args().size(); index < spec.params().size(); index++) {
            BuiltinSpec.ParamSpec param = spec.params().get(index);
            if (param.optional()) {
                SignatureInference.bindAbsent(param.typeExpr(), bounds);
            }
        }
        return new SignatureCall(List.copyOf(arguments), bounds);
    }

    /** 末尾 variadic 参数接收第 n 个及之后全部实参;超出声明且无 variadic 的多余实参不参与检查。 */
    private static BuiltinSpec.ParamSpec paramAt(BuiltinSpec spec, int index) {
        if (index >= 0 && index < spec.params().size()) {
            return spec.params().get(index);
        }
        if (!spec.params().isEmpty()) {
            BuiltinSpec.ParamSpec last = spec.params().get(spec.params().size() - 1);
            if (last.variadic()) {
                return last;
            }
        }
        return null;
    }

    /** 简洁签名的单参数检查:检查要求直接由 {@link SignatureInference#expectation} 从 TypeExpr 生成。 */
    private void checkSignatureParam(
            BuiltinSpec spec,
            AstNode.Comp call,
            int index,
            BuiltinSpec.ParamSpec param,
            Fact actual,
            AnalysisContext context) {
        Expectation expectation = SignatureInference.expectation(param.typeExpr());
        if (expectation == null) {
            return;
        }
        Checks.CheckResult result =
                session.checkObligation(
                        context.statementId(),
                        "@" + call.name() + "/args/" + index,
                        "param",
                        "/builtins/" + call.name() + "/params/" + index,
                        actual,
                        expectation,
                        null,
                        null,
                        "/args/" + index);
        if (result.verdict() == Checks.Verdict.MISMATCH && !actual.hasUpstreamErrors()) {
            context.emit(
                    "builtin-argument-type-mismatch",
                    "@"
                            + call.name()
                            + " argument "
                            + index
                            + " expects "
                            + param.type()
                            + ", but received "
                            + actual.type().displayName()
                            + ".",
                    null,
                    "/args/" + index,
                    "Use a value compatible with " + param.type() + ".");
        }
    }

    /** 按内部布局逐个求值实参;binder 位与模板位不按普通表达式检查。 */
    private List<Fact> argumentFacts(
            BuiltinSpec spec, AstNode.Comp call, AnalysisContext context, boolean checkParams) {
        if (checkParams) {
            checkArity(spec, call, context);
        }
        Layout layout = layoutOf(call.name(), call.args().size());
        List<Fact> arguments = new ArrayList<>();
        for (int index = 0; index < call.args().size(); index++) {
            AstNode argument = call.args().get(index);
            if (layout != null && layout.binderIndexes().contains(index)) {
                arguments.add(binderArgument(call, index, context));
                continue;
            }
            if (layout != null && index == layout.bodyIndex()) {
                // 模板位只能以绑定后的词法环境合成一次;这里占位保持实参下标对齐。
                arguments.add(Fact.unknown(Fact.Loss.MISSING_CONTEXT));
                continue;
            }
            Fact actual = context.synth(argument, context.env(), context.resolving());
            arguments.add(actual);
            if (checkParams && index < spec.params().size()) {
                checkSignatureParam(spec, call, index, spec.params().get(index), actual, context);
            }
        }
        return arguments;
    }

    private void checkArity(BuiltinSpec spec, AstNode.Comp call, AnalysisContext context) {
        int minimum = spec.minArity();
        Integer maximum = spec.maxArity().orElse(null);
        if (call.args().size() >= minimum && (maximum == null || call.args().size() <= maximum)) {
            return;
        }
        String expected =
                maximum == null
                        ? "at least " + minimum
                        : minimum == maximum ? String.valueOf(minimum) : minimum + "-" + maximum;
        context.emit(
                "builtin-argument-count-mismatch",
                "@"
                        + call.name()
                        + " expects "
                        + expected
                        + " argument(s), but received "
                        + call.args().size()
                        + ".",
                null,
                "/args",
                "Use the documented @" + call.name() + " signature.");
    }

    /** binder 参数:合法形态不产出类型事实(绑定发生在模板分析内);非法形态按普通表达式推断并报形态错。 */
    private Fact binderArgument(AstNode.Comp call, int index, AnalysisContext context) {
        if (binderName(call, index) != null) {
            return Fact.of(Types.scalar(Type.ScalarKind.STRING));
        }
        Fact actual = context.synth(call.args().get(index), context.env(), context.resolving());
        if (!actual.hasUpstreamErrors()) {
            context.emit(
                    "builtin-argument-type-mismatch",
                    "@"
                            + call.name()
                            + " argument "
                            + index
                            + " expects a binder name (string literal or bare identifier), but received "
                            + actual.type().displayName()
                            + ".",
                    null,
                    "/args/" + index,
                    "Use a string literal or a bare identifier as the binder name.");
        }
        return actual;
    }

    /** 有 Java 额外结果语义的普通 builtin:签名负责参数检查,结果证据与数量由既有 {@link TypeOps} 运算产生。 */
    private Fact dataSpecialResult(AstNode.Comp call, List<Fact> arguments) {
        Fact first =
                arguments.isEmpty() ? Fact.unknown(Fact.Loss.MISSING_CONTEXT) : arguments.get(0);
        return switch (call.name()) {
            case SORT -> session.ops().preserve(first);
            case FILTER -> session.ops().subset(first);
            case FIRST, LAST -> session.ops().select(first);
            case FLATTEN -> session.ops().flattenOne(first);
            case MERGE_OBJECT -> session.ops().overlay(arguments);
            case OBJECT_KEYS -> session.ops().keys(first);
            case OBJECT_VALUES -> session.ops().values(first);
            case OBJECT_ENTRIES -> session.ops().entries(first);
            case FORMAT_DATE -> session.ops().mapScalarOrArray(first);
            default -> Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
        };
    }

    /** Each:元素绑定 binder 检查 body,数量保留;空输入仍检查 body 中独立错误。 */
    private Fact eachResult(
            BuiltinSpec spec, AstNode.Comp call, List<Fact> arguments, AnalysisContext context) {
        Layout layout = layoutOf(call.name(), call.args().size());
        if (layout == null
                || arguments.isEmpty()
                || layout.inputIndex() < 0
                || layout.inputIndex() >= arguments.size()) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        Fact inputFact = arguments.get(layout.inputIndex());
        Fact element = session.ops().element(inputFact);
        boolean emptyPossible = lengthMayBeZero(inputFact);
        Fact boundElement = boundElement(inputFact, element, emptyPossible);
        // 保留元素存在性标记以约束缺字段诊断;标量类型误用仍可按安全上界检查。
        Fact checkElement = stripExistenceLosses(boundElement);
        AnalysisSession.LexicalEnvironment bodyEnv = bindElement(call, checkElement, context.env());
        Fact body =
                context.synth(call.args().get(layout.bodyIndex()), bodyEnv, context.resolving());
        if (emptyPossible) {
            body = body.withLoss(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        return session.ops().map(inputFact, body);
    }

    /** 元素存在性未证明时保留专用标记;Never 元素直接记为无元素证据。 */
    private static Fact boundElement(Fact inputFact, Fact element, boolean emptyPossible) {
        if (element.type() instanceof Type.Never) {
            return Fact.unknown(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        if (emptyPossible && !elementExistenceProven(inputFact, element)) {
            return element.withLoss(Fact.Loss.NO_ELEMENT_EVIDENCE);
        }
        return element;
    }

    /**
     * 元素存在已证明:直接观察到元素行,或字段读取的证据关联保留了快照中的嵌套行(#77 §6.5)。 无任何损失不等于观察到元素——Filter 删除保留行后元素类型仍在但存在性未证明。
     */
    private static boolean elementExistenceProven(Fact inputFact, Fact element) {
        if (!element.evidence().isEmpty()) {
            return true;
        }
        if (inputFact.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)) {
            return false;
        }
        for (Fact.Evidence item : inputFact.evidence()) {
            if (item instanceof Fact.Evidence.ObservedValue observed
                    && observed.value() instanceof List<?> values
                    && !values.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 元素绑定到第一个 binder;其余 binder 位保持 missing-context(binder 恒在输入来源之后,#83)。 */
    private static AnalysisSession.LexicalEnvironment bindElement(
            AstNode.Comp call, Fact element, AnalysisSession.LexicalEnvironment env) {
        AnalysisSession.LexicalEnvironment bodyEnv = env;
        Layout layout = layoutOf(call.name(), call.args().size());
        if (layout == null) {
            return bodyEnv;
        }
        for (int position = 0; position < layout.binderIndexes().size(); position++) {
            String name = binderName(call, layout.binderIndexes().get(position));
            if (name != null) {
                Fact bound = position == 0 ? element : Fact.unknown(Fact.Loss.MISSING_CONTEXT);
                bodyEnv = bodyEnv.bind(name, bound);
            }
        }
        return bodyEnv;
    }

    /** 去掉输入层面的未知标记,保留元素存在性标记与逐元素精度损失。 */
    private static Fact stripExistenceLosses(Fact fact) {
        if (!fact.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                && !fact.losses().contains(Fact.Loss.UNKNOWN_INPUT)) {
            return fact;
        }
        Set<Fact.Loss> losses = new LinkedHashSet<>(fact.losses());
        boolean existenceUnproven = losses.contains(Fact.Loss.NO_ELEMENT_EVIDENCE);
        losses.remove(Fact.Loss.UNKNOWN_INPUT);
        return new Fact(
                fact.type(),
                existenceUnproven ? Set.of() : fact.evidence(),
                losses,
                fact.upstreamErrors());
    }

    private static boolean lengthMayBeZero(Fact sequence) {
        for (Type branch : TypeOps.flatten(sequence.type())) {
            if (branch instanceof Type.Sequence seq && seq.minLength() == 0) {
                return true;
            }
            if (branch instanceof Type.Unknown) {
                return true;
            }
        }
        return false;
    }

    /**
     * Render:产生延迟模板产物;body 推迟到使用位置检查,类型只保留 Template 形状。 声明环境在这里捕获,Render 自身 binder 遮蔽同名捕获(#77 第
     * 10.3 节、ADR 0006)。
     */
    private Fact renderResult(BuiltinSpec spec, AstNode.Comp call, AnalysisContext context) {
        Layout layout = layoutOf(call.name(), call.args().size());
        if (layout == null) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        AnalysisSession.LexicalEnvironment declared = context.env();
        for (int binderIndex : layout.binderIndexes()) {
            String name = binderName(call, binderIndex);
            if (name != null) {
                declared = declared.bind(name, Fact.unknown(Fact.Loss.MISSING_CONTEXT));
            }
        }
        DeferredTemplate template =
                new DeferredTemplate(call, context.statementId(), declared, layout.bodyIndex());
        if (templates.putIfAbsent(call, template) == null) {
            templateOrder.add(template);
        }
        return Fact.of(Type.Template.INSTANCE);
    }

    /**
     * Switch:先按普通参数规则求值一次实参,结果为各 case 值与 default 的联合;未传 default 并入 null。
     *
     * <p>对象字面量逐项求值以保留诊断,结果只合并每个键最后一次赋值的事实;对象引用或数据路径能解出对象值类型时同样合并各值类型与 default。 无法解出对象值类型时保持未知。
     */
    private Fact switchResult(BuiltinSpec spec, AstNode.Comp call, AnalysisContext context) {
        checkArity(spec, call, context);
        List<Fact> arguments = new ArrayList<>();
        Map<String, Fact> caseEntries = null;
        for (int index = 0; index < call.args().size(); index++) {
            AstNode argument = call.args().get(index);
            Fact actual;
            if (index == 1 && argument instanceof AstNode.Obj cases) {
                caseEntries = new LinkedHashMap<>();
                Map<String, Type.Field> fields = new LinkedHashMap<>();
                Set<Fact.Loss> entryLosses = new LinkedHashSet<>();
                Set<String> entryUpstream = new LinkedHashSet<>();
                for (AstNode.Obj.Entry entry : cases.entries()) {
                    // 每个条目只分析一次;同名键只保留最后一个值的结果事实。
                    Fact entryFact =
                            context.synth(entry.value(), context.env(), context.resolving());
                    caseEntries.put(entry.key(), entryFact);
                    fields.put(entry.key(), Type.Field.required(entryFact.type()));
                    entryLosses.addAll(entryFact.losses());
                    entryUpstream.addAll(entryFact.upstreamErrors());
                }
                actual =
                        new Fact(
                                Types.record(fields, Type.Extra.closed()),
                                Set.of(),
                                entryLosses,
                                entryUpstream);
            } else {
                actual = context.synth(argument, context.env(), context.resolving());
            }
            arguments.add(actual);
            if (index < spec.params().size()) {
                checkSignatureParam(spec, call, index, spec.params().get(index), actual, context);
            }
        }
        if (call.args().size() < 2) {
            return Fact.unknown(Fact.Loss.MISSING_CONTEXT);
        }
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Set<String> upstream = new LinkedHashSet<>();
        List<Type> results = new ArrayList<>();
        if (caseEntries != null) {
            for (Fact entryFact : caseEntries.values()) {
                results.add(entryFact.type());
                losses.addAll(entryFact.losses());
                upstream.addAll(entryFact.upstreamErrors());
            }
        } else {
            Fact cases = arguments.get(1);
            losses.addAll(cases.losses());
            upstream.addAll(cases.upstreamErrors());
            for (Type branch : TypeOps.flatten(cases.type())) {
                if (!(branch instanceof Type.RecordType record)) {
                    return Fact.unknown(Fact.Loss.UNKNOWN_INPUT)
                            .withLosses(losses)
                            .withUpstreamErrors(upstream);
                }
                for (Type.Field field : record.fields().values()) {
                    results.add(field.type());
                }
                if (!record.extra().isClosed()) {
                    results.add(record.extra().valueType());
                }
            }
        }
        if (call.args().size() > 2) {
            Fact defaultFact = arguments.get(2);
            results.add(defaultFact.type());
            losses.addAll(defaultFact.losses());
            upstream.addAll(defaultFact.upstreamErrors());
        } else {
            results.add(Types.scalar(Type.ScalarKind.NULL));
        }
        return new Fact(Types.union(results), Set.of(), losses, upstream);
    }

    /**
     * 表格 cell 模板检查入口:模块自己解析命名引用、识别延迟模板、查找保存的表达式并绑定名称。
     *
     * @param cellExpression 列选项中的原 cell 表达式(可能是符号引用)
     * @param cellValue 单元格值事实
     * @param rowValue 整行值事实
     * @param context 使用位置上下文(语句、环境、诊断身份)
     */
    void checkCellTemplate(
            AstNode cellExpression, Fact cellValue, Fact rowValue, AnalysisContext context) {
        AstNode resolved = session.resolveNode(cellExpression, new LinkedHashSet<>());
        if (!(resolved instanceof AstNode.Comp template)
                || !RENDER.equals(template.name())
                || catalog.spec(template.name()) == null) {
            return;
        }
        Layout layout = layoutOf(template.name(), template.args().size());
        if (layout == null) {
            return;
        }
        // 每个使用位置分别检查:第 1 个 binder 绑定列字段,第 2 个绑定整行。
        List<String> names = new ArrayList<>();
        for (int binderIndex : layout.binderIndexes()) {
            String name = binderName(template, binderIndex);
            if (name != null) {
                names.add(name);
            }
        }
        Map<String, Fact> bindings = new LinkedHashMap<>();
        if (!names.isEmpty()) {
            bindings.put(names.get(0), cellValue);
        }
        if (names.size() > 1) {
            bindings.put(names.get(1), rowValue);
        }
        checkTemplateAtUseSite(template, bindings, context);
    }

    /** 在使用位置按提供的上下文检查模板 body:声明环境为底座叠加使用位置绑定,Render 自身 binder 再覆盖; 诊断与覆盖身份按使用位置区分。 */
    private void checkTemplateAtUseSite(
            AstNode.Comp render, Map<String, Fact> bindings, AnalysisContext context) {
        Layout layout = layoutOf(render.name(), render.args().size());
        if (layout == null) {
            return;
        }
        DeferredTemplate template = templates.get(render);
        AnalysisSession.LexicalEnvironment base =
                template == null ? context.env() : context.env().over(template.declarationEnv());
        AnalysisSession.LexicalEnvironment useEnv = base;
        for (int binderIndex : layout.binderIndexes()) {
            String name = binderName(render, binderIndex);
            if (name != null) {
                Fact provided =
                        bindings.getOrDefault(name, Fact.unknown(Fact.Loss.MISSING_CONTEXT));
                // 使用位置提供的上下文事实不携带数组层面的存在性丢失:逐元素判定只看值上界。
                useEnv = useEnv.bind(name, stripExistenceLosses(provided));
            }
        }
        contextCheckedTemplates.add(render);
        session.pushTemplateUseSite(
                context.useSitePath(),
                context.useSite(),
                context.statementId(),
                template == null ? null : template.statementId());
        try {
            session.synth(
                    render.args().get(layout.bodyIndex()),
                    context.statementId(),
                    useEnv,
                    context.resolving());
        } finally {
            session.popTemplateUseSite();
        }
    }

    /**
     * 根遍历结束后补查未在任何使用位置提供上下文的模板:以声明环境检查 body 的独立表达式,依赖 binder 的 部分保持 missing-context
     * 并计入覆盖。补查中发现的新模板继续处理,直到队列清空或预算耗尽。
     */
    void finish() {
        // 按下标遍历:body 内可能还有未分析的模板,追加到 templateOrder 的本轮继续处理。
        for (int index = 0; index < templateOrder.size(); index++) {
            DeferredTemplate template = templateOrder.get(index);
            if (contextCheckedTemplates.contains(template.call())) {
                continue;
            }
            if (!session.consumeCheck()) {
                session.markTraversalTruncated();
                return;
            }
            session.synth(
                    template.call().args().get(template.bodyIndex()),
                    template.statementId(),
                    template.declarationEnv(),
                    new LinkedHashSet<>());
        }
    }

    /**
     * Render 延迟模板产物(#77 第 10.3 节):body 位置、声明环境与声明语句只保存在本会话内,通用类型代数 只保留 {@link Type.Template} 形状,不把
     * AST 与环境塞进类型。
     *
     * @since 2026
     */
    private record DeferredTemplate(
            AstNode.Comp call,
            String statementId,
            AnalysisSession.LexicalEnvironment declarationEnv,
            int bodyIndex) {}
}
