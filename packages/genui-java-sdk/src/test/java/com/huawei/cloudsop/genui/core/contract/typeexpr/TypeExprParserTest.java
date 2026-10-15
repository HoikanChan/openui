/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract.typeexpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.ArrayOf;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.Base;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.ObjectLiteral;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.ParamRef;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.RecordOf;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.TypeVar;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr.UnionOf;

import org.junit.jupiter.api.Test;

/** 每条文法构造至少一合法一非法用例；错误信息须带条目名与原始串。 */
class TypeExprParserTest {

    @Test
    void parsesBaseTypes() {
        for (String base : new String[]{"any", "number", "string", "boolean", "null", "object", "array"}) {
            assertEquals(new Base(base), TypeExprParser.parse(base, "e"));
        }
    }

    @Test
    void rejectsUnknownTypeToken() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> TypeExprParser.parse("integer", "MyBuiltin.param"));
        assertTrue(error.getMessage().contains("MyBuiltin.param"), error.getMessage());
        assertTrue(error.getMessage().contains("integer"), error.getMessage());
    }

    @Test
    void parsesNestedArraySuffix() {
        assertEquals(new ArrayOf(new ArrayOf(new Base("string"))), TypeExprParser.parse("string[][]", "e"));
    }

    @Test
    void rejectsUnclosedArraySuffix() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("string[", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("string[3]", "e"));
    }

    @Test
    void parsesRecord() {
        assertEquals(new RecordOf(new TypeVar()), TypeExprParser.parse("Record<string, T>", "e"));
        assertEquals(new RecordOf(new ArrayOf(new Base("number"))),
                TypeExprParser.parse("Record<string, number[]>", "e"));
    }

    @Test
    void rejectsMalformedRecord() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("Record<string>", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("Record<number, T>", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("Record<string, T", "e"));
    }

    @Test
    void parsesObjectLiteral() {
        TypeExpr parsed = TypeExprParser.parse("{key: string, value: T}[]", "e");
        ObjectLiteral literal = (ObjectLiteral) ((ArrayOf) parsed).element();
        assertEquals(2, literal.fields().size());
        assertEquals("key", literal.fields().get(0).name());
        assertEquals(new Base("string"), literal.fields().get(0).type());
        assertEquals(new TypeVar(), literal.fields().get(1).type());
    }

    @Test
    void acceptsTrailingCommaInObjectLiteral() {
        TypeExpr parsed = TypeExprParser.parse("{a: number,}", "e");
        assertEquals(1, ((ObjectLiteral) parsed).fields().size());
    }

    @Test
    void rejectsObjectLiteralMissingColon() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("{a number}", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("{: number}", "e"));
    }

    @Test
    void parsesUnionOfTerms() {
        TypeExpr parsed = TypeExprParser.parse("string | string[]", "e");
        UnionOf union = (UnionOf) parsed;
        assertEquals(2, union.alternatives().size());
        assertEquals(new Base("string"), union.alternatives().get(0));
        assertEquals(new ArrayOf(new Base("string")), union.alternatives().get(1));
    }

    @Test
    void parsesUnionInsideRecordValue() {
        TypeExpr parsed = TypeExprParser.parse("Record<string, string | number>", "e");
        RecordOf record = (RecordOf) parsed;
        assertEquals(new UnionOf(java.util.List.of(new Base("string"), new Base("number"))), record.value());
    }

    @Test
    void rejectsDanglingUnionPipe() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("string |", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("| string", "e"));
    }

    @Test
    void parsesTypeVarAndParamRef() {
        assertEquals(new TypeVar(), TypeExprParser.parse("T", "e"));
        assertEquals(new ParamRef(2), TypeExprParser.parse("param(2)", "e"));
    }

    @Test
    void rejectsBadParamRef() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("param(x)", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("param(1", "e"));
    }

    @Test
    void rejectsTrailingTokens() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("string string", "e"));
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("number]", "e"));
    }

    @Test
    void rejectsUnexpectedCharacter() {
        assertThrows(GenerationSdkException.class, () -> TypeExprParser.parse("string & number", "e"));
    }

    @Test
    void errorMessageCarriesEntryAndSource() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> TypeExprParser.parse("bad type", "ObjectEntries.obj"));
        assertTrue(error.getMessage().contains("'ObjectEntries.obj'"), error.getMessage());
        assertTrue(error.getMessage().contains("bad type"), error.getMessage());
    }

    @Test
    void semanticEqualsIgnoresWhitespaceOnlyDifferences() {
        // 解析器已归一空白；同一构造的两次解析结构等值。
        assertTrue(TypeExprEquals.equals(TypeExprParser.parse("{key: string, value: T}[]", "a"),
                TypeExprParser.parse("{key: string, value: T}[]", "b")));
    }

    @Test
    void semanticEqualsMatchesObjectFieldsByName() {
        assertTrue(TypeExprEquals.equals(TypeExprParser.parse("{a: number, b: string}", "x"),
                TypeExprParser.parse("{b: string, a: number}", "y")));
    }

    @Test
    void semanticEqualsDistinguishesStructure() {
        org.junit.jupiter.api.Assertions.assertFalse(
                TypeExprEquals.equals(TypeExprParser.parse("number[]", "x"), TypeExprParser.parse("string[]", "y")));
        org.junit.jupiter.api.Assertions.assertFalse(
                TypeExprEquals.equals(TypeExprParser.parse("T", "x"), TypeExprParser.parse("number", "y")));
        org.junit.jupiter.api.Assertions.assertFalse(
                TypeExprEquals.equals(TypeExprParser.parse("param(0)", "x"), TypeExprParser.parse("param(1)", "y")));
    }
}
