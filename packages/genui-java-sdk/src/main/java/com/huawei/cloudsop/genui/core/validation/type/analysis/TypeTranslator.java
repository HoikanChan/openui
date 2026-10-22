package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.type.ValueType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旧 {@link ValueType}(数据路径解析器产物)到新 {@link Type} 的一次性翻译。
 *
 * <p>
 * 翻译保持上界安全:数组元素未知数量按 {@code 0..*};EmptyArrayType → 确知空数组;ObjectType 字段全 REQUIRED、
 * 尾部关闭;UnionType 逐分支翻译;StringDomainType → 字符串字面值集合。本类只服务数据快照接入,不向外暴露。
 *
 * @since 2026
 */
final class TypeTranslator {

    Type translate(ValueType valueType) {
        if (valueType instanceof ValueType.Primitive primitive) {
            return switch (primitive.kind()) {
                case STRING -> Types.scalar(Type.ScalarKind.STRING);
                case NUMBER -> Types.scalar(Type.ScalarKind.NUMBER);
                case BOOLEAN -> Types.scalar(Type.ScalarKind.BOOLEAN);
                case NULL -> Types.scalar(Type.ScalarKind.NULL);
            };
        }
        if (valueType instanceof ValueType.StringDomainType domain) {
            return Types.stringLiterals(domain.values());
        }
        if (valueType instanceof ValueType.EmptyArrayType) {
            return Types.emptySequence();
        }
        if (valueType instanceof ValueType.ArrayType array) {
            return Types.sequence(translate(array.elementType()), 0, Type.Sequence.UNBOUNDED);
        }
        if (valueType instanceof ValueType.ObjectType object) {
            Map<String, Type.Field> fields = new LinkedHashMap<>();
            object.fields().forEach((name, fieldType) -> fields.put(name, Type.Field.required(translate(fieldType))));
            return Types.record(fields, Type.Extra.closed());
        }
        if (valueType instanceof ValueType.UnionType union) {
            return Types.union(union.alternatives().stream().map(this::translate).toList());
        }
        if (valueType instanceof ValueType.ComponentType component) {
            return new Type.Component(component.name());
        }
        if (valueType instanceof ValueType.TemplateType) {
            return Type.Template.INSTANCE;
        }
        return Type.Unknown.INSTANCE;
    }
}
