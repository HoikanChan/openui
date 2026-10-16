/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * openui-lang 的 token 类型判别符。
 *
 * <p>
 * 对应 {@code packages/lang-core/src/parser/tokens.ts} 中的 TypeScript {@code const enum T}。序数顺序与 TS
 * 枚举保持一致，以便跨语言对齐测试数据能够一一对应。
 *
 * @since 2026
 */
public enum TokenType {
    /** {@code \n}（有意义 —— 深度为 0 时作为语句分隔符）。 */
    NEWLINE,
    /** {@code (}。 */
    L_PAREN,
    /** {@code )}。 */
    R_PAREN,
    /** {@code [}。 */
    L_BRACK,
    /** {@code ]}。 */
    R_BRACK,
    /** 左花括号 {@code &#123;}。 */
    L_BRACE,
    /** 右花括号 {@code &#125;}。 */
    R_BRACE,
    /** {@code ,}。 */
    COMMA,
    /** {@code :}。 */
    COLON,
    /** {@code =}。 */
    EQUALS,
    /** {@code true}。 */
    TRUE,
    /** {@code false}。 */
    FALSE,
    /** {@code null}。 */
    NULL,
    /** 输入结束。 */
    EOF,
    /** 字符串字面量（携带值）。 */
    STR,
    /** 数字字面量（携带值）。 */
    NUM,
    /** 小写标识符 —— 会成为一个引用。 */
    IDENT,
    /** 帕斯卡命名标识符 —— 组件名或类型引用。 */
    TYPE,
    /** {@code $identifier} —— 响应式状态引用（值包含前导 {@code $}）。 */
    STATE_VAR,
    /** {@code .}。 */
    DOT,
    /** {@code +}。 */
    PLUS,
    /** {@code -}。 */
    MINUS,
    /** {@code *}。 */
    STAR,
    /** {@code /}。 */
    SLASH,
    /** {@code %}。 */
    PERCENT,
    /** {@code ==}。 */
    EQ_EQ,
    /** {@code !=}。 */
    NOT_EQ,
    /** {@code >}。 */
    GREATER,
    /** {@code <}。 */
    LESS,
    /** {@code >=}。 */
    GREATER_EQ,
    /** {@code <=}。 */
    LESS_EQ,
    /** {@code &&}。 */
    AND,
    /** {@code ||}。 */
    OR,
    /** {@code !}。 */
    NOT,
    /** {@code ?}。 */
    QUESTION,
    /** {@code @identifier} —— 内置函数调用（值不含前导 {@code @}）。 */
    BUILTIN_CALL,
    /** {@code ??}。 */
    NULL_COAL
}
