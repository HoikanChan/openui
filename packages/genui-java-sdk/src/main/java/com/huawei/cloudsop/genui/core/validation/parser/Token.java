/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * 词法 token。
 *
 * <p>
 * 对应 TS 侧的 {@code Token{t, v}}，但作为 Java 扩展额外增加了源码位置字段（{@code line}、{@code column}、
 * {@code offset}、{@code length}），使诊断信息可携带行/列/范围。{@code line}/{@code column} 从 1 开始计数，
 * {@code offset} 从 0 开始（预处理后源文本中的下标）。
 *
 * <p>
 * {@code text} 携带 {@link TokenType#STR}、{@link TokenType#IDENT}、{@link TokenType#TYPE}、
 * {@link TokenType#STATE_VAR}（含前导 {@code $}）以及 {@link TokenType#BUILTIN_CALL}（不含前导 {@code @}）的解码
 * 字符串值。{@code number} 携带 {@link TokenType#NUM} 的解析数值。标点符号类 token 两者分别为 {@code null}/
 * {@code NaN}。
 *
 * @param type
 *            token 类型
 * @param text
 *            解码后的字符串值，标点符号类 token 为 {@code null}
 * @param number
 *            解析后的数值，非数字 token 为 {@code NaN}
 * @param line
 *            起始行号
 * @param column
 *            起始列号
 * @param offset
 *            起始偏移量
 * @param length
 *            token 长度
 *
 * @since 2026
 */
public record Token(TokenType type, String text, double number, int line, int column, int offset, int length) {

    /**
     * 创建一个不携带值的标点/关键字 token。
     *
     * @param type
     *            token 类型
     * @param line
     *            起始行号
     * @param column
     *            起始列号
     * @param offset
     *            起始偏移量
     * @param length
     *            token 长度
     * @return token 实例
     */
    public static Token of(TokenType type, int line, int column, int offset, int length) {
        return new Token(type, null, Double.NaN, line, column, offset, length);
    }

    /**
     * 创建一个携带字符串值的 token（{@code STR}/{@code IDENT}/{@code TYPE} 等）。
     *
     * @param type
     *            token 类型
     * @param text
     *            字符串值
     * @param line
     *            起始行号
     * @param column
     *            起始列号
     * @param offset
     *            起始偏移量
     * @param length
     *            token 长度
     * @return token 实例
     */
    public static Token ofText(TokenType type, String text, int line, int column, int offset, int length) {
        return new Token(type, text, Double.NaN, line, column, offset, length);
    }

    /**
     * 创建一个数字 token。
     *
     * @param number
     *            解析出的数值
     * @param line
     *            起始行号
     * @param column
     *            起始列号
     * @param offset
     *            起始偏移量
     * @param length
     *            token 长度
     * @return token 实例
     */
    public static Token ofNumber(double number, int line, int column, int offset, int length) {
        return new Token(TokenType.NUM, null, number, line, column, offset, length);
    }
}
