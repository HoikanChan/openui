package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;

/**
 * The result of parsing an openui-lang source string.
 *
 * <p>The TS side calls this a {@code ParseResult} and folds materialization/schema validation into
 * it; here the parser stops at the syntactic layer. A {@code Program} therefore carries the ordered
 * list of typed {@link Statement}s plus structured {@link ParseDiagnostic}s. Section 3 consumes this
 * for semantic/contract validation.
 *
 * @param statements parsed statements, in source order; never {@code null}
 * @param diagnostics structured syntax diagnostics; never {@code null}
 * @param incomplete {@code true} if auto-close had to repair unclosed brackets/strings (streaming or
 *     truncated input)
 */
public record Program(
    List<Statement> statements, List<ParseDiagnostic> diagnostics, boolean incomplete) {

  public Program {
    statements = statements == null ? List.of() : List.copyOf(statements);
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  /** {@code true} iff no diagnostics were collected. */
  public boolean isClean() {
    return diagnostics.isEmpty();
  }
}
