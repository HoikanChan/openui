/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TextBlock;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTag;
import com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocTagInfo;
import com.puppycrawl.tools.checkstyle.utils.AnnotationUtil;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;
import com.puppycrawl.tools.checkstyle.utils.JavadocUtil;
import com.puppycrawl.tools.checkstyle.utils.TokenUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the existing Javadoc of record methods and constructors.
 *
 * <p>Checkstyle's {@code JavadocMethod} resolves the surrounding access modifier only through
 * class, interface, annotation and enum definitions. A record that is not nested in one of those
 * therefore has no surrounding access modifier, so {@code JavadocMethod} skips every member. This
 * check covers exactly that gap and delegates nothing else, which keeps it from duplicating {@code
 * JavadocMethod} on ordinary types.
 *
 * <p>Only members that already have Javadoc are inspected; missing Javadoc remains the
 * responsibility of {@code MissingJavadocMethod}. Tag handling mirrors {@code JavadocMethod} for
 * {@code @param}, {@code @return}, {@code @throws}/{@code @exception} and {@code @inheritDoc}.
 *
 * @since 2026
 */
public class RecordJavadocMethodCheck extends AbstractCheck {

    /** Message key for a missing tag. */
    public static final String MSG_EXPECTED_TAG = "javadoc.expectedTag";

    /** Message key for a missing return tag. */
    public static final String MSG_RETURN_EXPECTED = "javadoc.return.expected";

    /** Message key for an unused parameter tag. */
    public static final String MSG_UNUSED_TAG = "javadoc.unusedTag";

    /** Message key for an invalid inherit-doc tag. */
    public static final String MSG_INVALID_INHERIT_DOC = "javadoc.invalidInheritDoc";

    /** Whether missing throws tags are reported. */
    private boolean validateThrows = true;

    /** Whether missing parameter tags are allowed. */
    private boolean allowMissingParamTags;

    /** Whether a missing return tag is allowed. */
    private boolean allowMissingReturnTag;

    /** Annotations that suppress the expected-tag checks. */
    private Set<String> allowedAnnotations = Set.of("Override");

    /**
     * Sets whether missing throws tags are reported.
     *
     * @param value whether throws tags are validated
     */
    public void setValidateThrows(boolean value) {
        validateThrows = value;
    }

    /**
     * Sets whether a missing parameter tag is allowed.
     *
     * @param value whether missing parameter tags are allowed
     */
    public void setAllowMissingParamTags(boolean value) {
        allowMissingParamTags = value;
    }

    /**
     * Sets whether a missing return tag is allowed.
     *
     * @param value whether a missing return tag is allowed
     */
    public void setAllowMissingReturnTag(boolean value) {
        allowMissingReturnTag = value;
    }

    /**
     * Sets the annotations that suppress the expected-tag checks.
     *
     * @param userAnnotations annotation names
     */
    public void setAllowedAnnotations(String... userAnnotations) {
        allowedAnnotations = Set.of(userAnnotations);
    }

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.METHOD_DEF, TokenTypes.CTOR_DEF, TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtil.EMPTY_INT_ARRAY;
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (!isRecordMemberMissedByJavadocMethod(ast)) {
            return;
        }
        final TextBlock comment = getFileContents().getJavadocBefore(ast.getLineNo());
        if (comment == null) {
            return;
        }
        final List<JavadocTag> tags = methodTags(comment);
        if (hasOnlyInheritDoc(tags)) {
            if (!JavadocTagInfo.INHERIT_DOC.isValidOn(ast)) {
                log(ast, MSG_INVALID_INHERIT_DOC);
            }
            return;
        }
        final boolean reportExpected =
                !hasInheritDoc(tags) && !AnnotationUtil.containsAnnotation(ast, allowedAnnotations);
        if (ast.getType() != TokenTypes.COMPACT_CTOR_DEF) {
            checkParamTags(tags, ast, reportExpected);
        }
        checkThrowsTags(tags, ast, reportExpected);
        checkReturnTag(tags, ast, reportExpected);
    }

    /**
     * Returns whether a node is skipped by {@code JavadocMethod} because it sits in a record and no
     * enclosing class, interface, annotation or enum stops the access-modifier lookup.
     *
     * @param ast method or constructor node
     * @return whether this check is responsible for the node
     */
    private static boolean isRecordMemberMissedByJavadocMethod(DetailAST ast) {
        boolean inRecord = false;
        DetailAST token = ast.getParent();
        while (token != null && !TokenUtil.isRootNode(token)) {
            final int type = token.getType();
            if (type == TokenTypes.CLASS_DEF
                    || type == TokenTypes.INTERFACE_DEF
                    || type == TokenTypes.ANNOTATION_DEF
                    || type == TokenTypes.ENUM_DEF
                    || type == TokenTypes.LITERAL_NEW) {
                return false;
            }
            if (type == TokenTypes.RECORD_DEF) {
                inRecord = true;
            }
            token = token.getParent();
        }
        return inRecord;
    }

    /**
     * Extracts the method tags that {@code JavadocMethod} also considers.
     *
     * <p>Empty {@code @param}/{@code @throws} tags and {@code @return} tags without a description
     * are dropped, matching {@code JavadocMethod}, which does not treat them as satisfying tags.
     *
     * @param comment Javadoc block
     * @return relevant tags
     */
    private static List<JavadocTag> methodTags(TextBlock comment) {
        final List<JavadocTag> tags = new ArrayList<>();
        for (JavadocTag tag :
                JavadocUtil.getJavadocTags(comment, JavadocUtil.JavadocTagType.ALL)
                        .getValidTags()) {
            if (tag.isInheritDocTag()) {
                tags.add(tag);
            } else if (tag.isParamTag() || tag.isThrowsTag() || tag.isReturnTag()) {
                if (!tagArgument(tag).isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    /**
     * Returns whether the tag list contains only a single inherit-doc tag.
     *
     * @param tags parsed Javadoc tags
     * @return whether the Javadoc short-circuits validation
     */
    private static boolean hasOnlyInheritDoc(List<JavadocTag> tags) {
        return tags.size() == 1 && tags.get(0).isInheritDocTag();
    }

    /**
     * Returns whether the tag list contains an inherit-doc tag.
     *
     * @param tags parsed Javadoc tags
     * @return whether the Javadoc inherits its documentation
     */
    private static boolean hasInheritDoc(List<JavadocTag> tags) {
        return tags.stream().anyMatch(JavadocTag::isInheritDocTag);
    }

    /**
     * Checks parameter tags against the declared parameters and type parameters.
     *
     * @param tags parsed Javadoc tags, consumed in place
     * @param ast method or constructor node
     * @param reportExpected whether missing tags are reported
     */
    private void checkParamTags(List<JavadocTag> tags, DetailAST ast, boolean reportExpected) {
        final List<DetailAST> params = identChildren(ast.findFirstToken(TokenTypes.PARAMETERS));
        final List<DetailAST> typeParams = typeParameterIdents(ast);
        final Iterator<JavadocTag> iterator = tags.iterator();
        while (iterator.hasNext()) {
            final JavadocTag tag = iterator.next();
            if (!tag.isParamTag()) {
                continue;
            }
            iterator.remove();
            final String name = tagArgument(tag);
            if (!removeMatching(params, name) && !removeMatchingTypeParam(typeParams, name)) {
                log(tag.getLineNo(), tag.getColumnNo(), MSG_UNUSED_TAG, "param", name);
            }
        }
        if (!allowMissingParamTags && reportExpected) {
            for (DetailAST param : params) {
                log(param, MSG_EXPECTED_TAG, "param", param.getText());
            }
            for (DetailAST typeParam : typeParams) {
                log(typeParam, MSG_EXPECTED_TAG, "param", "<" + typeParam.getText() + ">");
            }
        }
    }

    /**
     * Checks that every declared or thrown exception has a throws tag.
     *
     * @param tags parsed Javadoc tags, consumed in place
     * @param ast method or constructor node
     * @param reportExpected whether missing tags are reported
     */
    private void checkThrowsTags(List<JavadocTag> tags, DetailAST ast, boolean reportExpected) {
        final Set<String> thrown = thrownExceptionNames(ast);
        final Iterator<JavadocTag> iterator = tags.iterator();
        while (iterator.hasNext()) {
            final JavadocTag tag = iterator.next();
            if (tag.isThrowsTag()) {
                iterator.remove();
                removeMatchingName(thrown, tagArgument(tag));
            }
        }
        if (validateThrows && reportExpected) {
            for (String name : thrown) {
                log(ast, MSG_EXPECTED_TAG, "throws", name);
            }
        }
    }

    /**
     * Checks that a non-void method has a return tag.
     *
     * @param tags parsed Javadoc tags
     * @param ast method node
     * @param reportExpected whether missing tags are reported
     */
    private void checkReturnTag(List<JavadocTag> tags, DetailAST ast, boolean reportExpected) {
        if (!CheckUtil.isNonVoidMethod(ast)) {
            return;
        }
        final boolean found = tags.stream().anyMatch(JavadocTag::isReturnTag);
        if (!found && !allowMissingReturnTag && reportExpected) {
            log(ast, MSG_RETURN_EXPECTED);
        }
    }

    /**
     * Collects the de-duplicated simple names of declared and thrown exceptions.
     *
     * @param ast method or constructor node
     * @return exception simple names
     */
    private static Set<String> thrownExceptionNames(DetailAST ast) {
        final Set<String> names = new LinkedHashSet<>();
        final DetailAST throwsAst = ast.findFirstToken(TokenTypes.LITERAL_THROWS);
        if (throwsAst != null) {
            for (DetailAST child = throwsAst.getFirstChild();
                    child != null;
                    child = child.getNextSibling()) {
                addThrownName(names, child);
            }
        }
        final DetailAST body = ast.findFirstToken(TokenTypes.SLIST);
        if (body != null) {
            for (DetailAST throwAst :
                    JavadocMethodCheck.findTokensInAstByType(body, TokenTypes.LITERAL_THROW)) {
                final DetailAST expression = throwAst.getFirstChild();
                final DetailAST created =
                        expression == null
                                ? null
                                : expression.findFirstToken(TokenTypes.LITERAL_NEW);
                if (created != null && !isInIgnoredBlock(body, throwAst)) {
                    addThrownName(names, created.getFirstChild());
                }
            }
        }
        return names;
    }

    /**
     * Adds the simple class name of an exception reference.
     *
     * @param names collected names
     * @param node identifier or qualified type node
     */
    private static void addThrownName(Set<String> names, DetailAST node) {
        if (node != null
                && (node.getType() == TokenTypes.IDENT || node.getType() == TokenTypes.DOT)) {
            final String text = FullIdent.createFullIdent(node).getText();
            names.add(text.substring(text.lastIndexOf('.') + 1));
        }
    }

    /**
     * Returns whether a throw statement sits in a block that {@code JavadocMethod} ignores.
     *
     * @param body method body
     * @param throwAst throw statement
     * @return whether the throw is ignored
     */
    private static boolean isInIgnoredBlock(DetailAST body, DetailAST throwAst) {
        DetailAST ancestor = throwAst;
        while (ancestor != body) {
            if (ancestor.getType() == TokenTypes.LAMBDA
                    || ancestor.getType() == TokenTypes.OBJBLOCK
                    || ancestor.findFirstToken(TokenTypes.LITERAL_CATCH) != null) {
                break;
            }
            if (ancestor.getType() == TokenTypes.LITERAL_CATCH
                    || ancestor.getType() == TokenTypes.LITERAL_FINALLY) {
                ancestor = ancestor.getParent();
            }
            ancestor = ancestor.getParent();
        }
        return ancestor != body;
    }

    /**
     * Returns the identifier children of a node.
     *
     * @param parent parent node, may be null
     * @return identifier nodes
     */
    private static List<DetailAST> identChildren(DetailAST parent) {
        final List<DetailAST> idents = new ArrayList<>();
        if (parent != null) {
            for (DetailAST child = parent.getFirstChild();
                    child != null;
                    child = child.getNextSibling()) {
                final DetailAST ident = child.findFirstToken(TokenTypes.IDENT);
                if (ident != null) {
                    idents.add(ident);
                }
            }
        }
        return idents;
    }

    /**
     * Returns the identifiers of a node's type parameters.
     *
     * @param ast method or constructor node
     * @return type parameter identifiers
     */
    private static List<DetailAST> typeParameterIdents(DetailAST ast) {
        return CheckUtil.getTypeParameters(ast).stream()
                .map(typeParam -> typeParam.findFirstToken(TokenTypes.IDENT))
                .filter(ident -> ident != null)
                .toList();
    }

    /**
     * Returns the first argument of a tag, without its description.
     *
     * @param tag parsed Javadoc tag
     * @return first whitespace-delimited token of the tag value
     */
    private static String tagArgument(JavadocTag tag) {
        final String value = tag.getFirstArg();
        if (value == null) {
            return "";
        }
        final String trimmed = value.trim();
        final int end = trimmed.indexOf(' ');
        return end < 0 ? trimmed : trimmed.substring(0, end);
    }

    /**
     * Removes the identifier whose text equals the given name.
     *
     * @param idents identifier nodes, consumed in place
     * @param name name to match
     * @return whether a match was removed
     */
    private static boolean removeMatching(List<DetailAST> idents, String name) {
        final Iterator<DetailAST> iterator = idents.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getText().equals(name)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a type parameter tag such as {@code <T>}.
     *
     * @param typeParams type parameter identifiers, consumed in place
     * @param name tag argument
     * @return whether a match was removed
     */
    private static boolean removeMatchingTypeParam(List<DetailAST> typeParams, String name) {
        if (name.startsWith("<") && name.endsWith(">")) {
            return removeMatching(typeParams, name.substring(1, name.length() - 1));
        }
        return false;
    }

    /**
     * Removes a thrown exception name matching the documented name.
     *
     * @param names thrown exception names, consumed in place
     * @param documented documented name
     */
    private static void removeMatchingName(Set<String> names, String documented) {
        final String simpleName = documented.substring(documented.lastIndexOf('.') + 1);
        names.remove(documented);
        names.remove(simpleName);
    }
}
