package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ArrayConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ComponentConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.EnumConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ObjectConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.PrimitiveConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.UnionConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Fail-open adapter from supported Generation Contract schema fragments to expected types. */
final class SchemaTypeCompiler {

    Optional<ExpectedType> compile(Object rawSchema) {
        if (!(rawSchema instanceof Map<?, ?> rawMap)) {
            return Optional.empty();
        }
        Map<?, ?> schema = rawMap;

        Object variantsRaw = schema.get("anyOf");
        if (!(variantsRaw instanceof List<?>)) {
            variantsRaw = schema.get("oneOf");
        }
        if (variantsRaw instanceof List<?> variants) {
            List<ExpectedType> compiled = new ArrayList<>();
            for (Object variant : variants) {
                Optional<ExpectedType> result = compile(variant);
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                compiled.add(result.get());
            }
            return Optional.of(new UnionConstraint(compiled));
        }

        Object component = schema.get("component");
        if (component != null) {
            return Optional.of(new ComponentConstraint(String.valueOf(component)));
        }

        String type = schema.get("type") == null ? null : String.valueOf(schema.get("type"));
        if ("string".equals(type)) {
            Object enumRaw = schema.get("enum");
            if (enumRaw instanceof List<?> values) {
                Set<String> literals = new LinkedHashSet<>();
                values.forEach(value -> literals.add(String.valueOf(value)));
                return Optional.of(new EnumConstraint(literals));
            }
            return Optional.of(new PrimitiveConstraint(PrimitiveKind.STRING));
        }
        if ("number".equals(type) || "integer".equals(type)) {
            return Optional.of(new PrimitiveConstraint(PrimitiveKind.NUMBER));
        }
        if ("boolean".equals(type)) {
            return Optional.of(new PrimitiveConstraint(PrimitiveKind.BOOLEAN));
        }
        if ("null".equals(type)) {
            return Optional.of(new PrimitiveConstraint(PrimitiveKind.NULL));
        }
        if ("array".equals(type)) {
            Object itemSchema = schema.get("items");
            Optional<ExpectedType> item = compile(itemSchema);
            return Optional.of(new ArrayConstraint(item.orElse(null)));
        }
        if ("object".equals(type)) {
            Map<String, ExpectedType> fields = new LinkedHashMap<>();
            Object propertiesRaw = schema.get("properties");
            if (propertiesRaw instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    Optional<ExpectedType> field = compile(entry.getValue());
                    field.ifPresent(value -> fields.put(String.valueOf(entry.getKey()), value));
                }
            }
            return Optional.of(new ObjectConstraint(fields));
        }
        return Optional.empty();
    }
}
