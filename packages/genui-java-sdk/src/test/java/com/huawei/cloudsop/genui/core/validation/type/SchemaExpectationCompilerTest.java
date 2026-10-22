/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** Schema-subset compilation (#82): supported keywords compile fully; unsupported keywords are preserved, not dropped. */
class SchemaExpectationCompilerTest {

    private final SchemaExpectationCompiler compiler = new SchemaExpectationCompiler();

    @Test
    void compilesAnyOfAsCompositeNotUnion() {
        Expectation e = compiler.compile(Map.of("anyOf",
                List.of(Map.of("type", "number"), Map.of("type", "string"))));
        assertInstanceOf(Expectation.AnyOf.class, e);
    }

    @Test
    void compilesOneOfAsExactlyOneNotUnion() {
        Expectation e = compiler.compile(Map.of("oneOf",
                List.of(Map.of("type", "number"), Map.of("type", "string"))));
        assertInstanceOf(Expectation.ExactlyOne.class, e);
    }

    @Test
    void preservesUnsupportedKeywordInsteadOfDropping() {
        // minLength is not in the supported subset; it must be preserved as Unsupported, not silently dropped.
        Expectation e = compiler.compile(Map.of("type", "string", "minLength", 2));
        assertTrue(containsUnsupported(e), () -> "minLength must be preserved: " + e);
    }

    @Test
    void unsupportedItemSchemaIsNotTurnedIntoUnconstrained() {
        Expectation e = compiler.compile(Map.of("type", "array", "items", Map.of("type", "string", "minLength", 2)));
        assertTrue(containsUnsupported(e), () -> "uncompilable items must stay Unsupported: " + e);
    }

    @Test
    void requiredFieldNotInPropertiesStillRequired() {
        Expectation e = compiler.compile(
                Map.of("type", "object", "required", List.of("id"), "properties", Map.of()));
        assertTrue(hasRequiredField(e, "id"), () -> "required must hold even without properties entry: " + e);
    }

    @Test
    void additionalPropertiesFalseClosesObject() {
        // additionalProperties:false marks the object closed; the closedObject requirement is preserved (a Predicate).
        Expectation e = compiler.compile(Map.of("type", "object", "properties", Map.of(),
                "additionalProperties", false));
        assertEquals(new Expectation.Predicate("closedObject", List.of()), e);
    }

    @Test
    void supportedPartStillCheckedWhenUnsupportedPresent() {
        // A schema with both supported (type:number) and unsupported (pattern) keeps the number check.
        Expectation e = compiler.compile(Map.of("type", "number", "pattern", "^x"));
        assertTrue(hasAccept(e), () -> "supported number part must survive: " + e);
        assertTrue(containsUnsupported(e));
    }

    @Test
    void stringEnumCompilesToLiterals() {
        Expectation e = compiler.compile(Map.of("type", "string", "enum", List.of("a", "b")));
        assertInstanceOf(Expectation.Accept.class, e);
        assertEquals(new ValueType.StringDomainType(java.util.Set.of("a", "b")), ((Expectation.Accept) e).type());
    }

    @Test
    void componentExtensionCompiles() {
        Expectation e = compiler.compile(Map.of("component", "Col"));
        assertEquals(new Expectation.Accept(new ValueType.ComponentType("Col")), e);
    }

    private static boolean containsUnsupported(Expectation e) {
        if (e instanceof Expectation.Unsupported) {
            return true;
        }
        if (e instanceof Expectation.AllOf all) {
            return all.parts().stream().anyMatch(SchemaExpectationCompilerTest::containsUnsupported);
        }
        if (e instanceof Expectation.AnyOf any) {
            return any.candidates().stream().anyMatch(SchemaExpectationCompilerTest::containsUnsupported);
        }
        if (e instanceof Expectation.ExactlyOne one) {
            return one.candidates().stream().anyMatch(SchemaExpectationCompilerTest::containsUnsupported);
        }
        if (e instanceof Expectation.RequireField req) {
            return containsUnsupported(req.expectation());
        }
        if (e instanceof SchemaExpectationCompiler.ElementRequirement el) {
            return containsUnsupported(el.element());
        }
        if (e instanceof SchemaExpectationCompiler.ExtraFieldsRequirement ex) {
            return containsUnsupported(ex.extra());
        }
        return false;
    }

    private static boolean hasAccept(Expectation e) {
        if (e instanceof Expectation.Accept) {
            return true;
        }
        if (e instanceof Expectation.AllOf all) {
            return all.parts().stream().anyMatch(SchemaExpectationCompilerTest::hasAccept);
        }
        return false;
    }

    private static boolean hasRequiredField(Expectation e, String name) {
        if (e instanceof Expectation.RequireField req) {
            return req.name().equals(name) || hasRequiredField(req.expectation(), name);
        }
        if (e instanceof Expectation.AllOf all) {
            return all.parts().stream().anyMatch(p -> hasRequiredField(p, name));
        }
        return false;
    }
}
