package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Pratt (top-down operator-precedence) expression parser for openui-lang.
 *
 * <p>Mirrors {@code parseExpression()} in {@code packages/lang-core/src/parser/expressions.ts}:
 * identical precedence levels and operator handling, component/builtin calls, arrays/objects, refs,
 * member/index access, unary, binary, null-coalescing and ternary.
 *
 * <p>Unlike the TS oracle, this parser additionally COLLECTS structured {@link ParseDiagnostic}s
 * (unclosed brackets, unexpected tokens) instead of silently swallowing them, while preserving the
 * same tolerant AST output so later statements still parse. Diagnostics are read via {@link
 * #diagnostics()} after {@link #parse()}.
 */
public final class ExpressionParser {

  // Precedence levels (from spec Section 2.11 / expressions.ts).
  private static final int PREC_TERNARY = 1;
  private static final int PREC_OR = 2;
  private static final int PREC_AND = 3;
  private static final int PREC_EQ = 4;
  private static final int PREC_CMP = 5;
  private static final int PREC_ADD = 6;
  private static final int PREC_MUL = 7;
  private static final int PREC_UNARY = 8;
  private static final int PREC_MEMBER = 9;

  private final List<Token> tokens;
  private final List<ParseDiagnostic> diagnostics = new ArrayList<>();
  private final String statementId;
  private int pos;

  public ExpressionParser(List<Token> tokens) {
    this(tokens, null);
  }

  public ExpressionParser(List<Token> tokens, String statementId) {
    this.tokens = tokens;
    this.statementId = statementId;
  }

  /** Diagnostics collected during the last {@link #parse()}. */
  public List<ParseDiagnostic> diagnostics() {
    return List.copyOf(diagnostics);
  }

  /** Parse the token stream into a single AST node. */
  public AstNode parse() {
    return parseExpr(0);
  }

  // ── Cursor ────────────────────────────────────────────────────────────────
  private Token cur() {
    return pos < tokens.size() ? tokens.get(pos) : eofToken();
  }

  private Token peek(int ahead) {
    int idx = pos + ahead;
    return idx < tokens.size() ? tokens.get(idx) : eofToken();
  }

  private Token eofToken() {
    return Token.of(TokenType.EOF, -1, -1, -1, 0);
  }

  private Token adv() {
    Token t = cur();
    pos++;
    return t;
  }

  /** Consume a token of {@code kind}; if absent, record an unclosed-bracket diagnostic. */
  private void eatClosing(TokenType kind, ParseErrorCode code, String what, Token opener) {
    if (cur().type() == kind) {
      pos++;
    } else {
      diagnostics.add(
          ParseDiagnostic.at(
              code,
              "Unclosed " + what,
              statementId,
              new SourceSpan(opener.line(), opener.column(), opener.offset(),
                  opener.offset() + Math.max(0, opener.length()))));
    }
  }

  private void eatOptional(TokenType kind) {
    if (cur().type() == kind) {
      pos++;
    }
  }

  // ── Infix precedence lookup ────────────────────────────────────────────────
  private static int infixPrec(Token tok) {
    return switch (tok.type()) {
      case QUESTION -> PREC_TERNARY;
      case NULL_COAL, OR -> PREC_OR;
      case AND -> PREC_AND;
      case EQ_EQ, NOT_EQ -> PREC_EQ;
      case GREATER, LESS, GREATER_EQ, LESS_EQ -> PREC_CMP;
      case PLUS, MINUS -> PREC_ADD;
      case STAR, SLASH, PERCENT -> PREC_MUL;
      case DOT, L_BRACK -> PREC_MEMBER;
      default -> 0;
    };
  }

  // ── Main Pratt loop ─────────────────────────────────────────────────────────
  private AstNode parseExpr(int minPrec) {
    AstNode left = parsePrefix();
    while (infixPrec(cur()) > minPrec) {
      left = parseInfix(left);
    }
    return left;
  }

  // ── Prefix / atoms ──────────────────────────────────────────────────────────
  private AstNode parsePrefix() {
    Token tok = cur();
    switch (tok.type()) {
      case STR -> {
        adv();
        return new AstNode.Str(tok.text());
      }
      case NUM -> {
        adv();
        return new AstNode.Num(tok.number());
      }
      case TRUE -> {
        adv();
        return new AstNode.Bool(true);
      }
      case FALSE -> {
        adv();
        return new AstNode.Bool(false);
      }
      case NULL -> {
        adv();
        return new AstNode.Null();
      }
      case L_BRACK -> {
        return parseArr();
      }
      case L_BRACE -> {
        return parseObj();
      }
      case STATE_VAR -> {
        String name = tok.text();
        adv();
        if (cur().type() == TokenType.EQUALS) {
          adv(); // consume '='
          AstNode value = parseExpr(0);
          return new AstNode.Assign(name, value);
        }
        return new AstNode.StateRef(name);
      }
      case TYPE -> {
        String name = tok.text();
        // Builtins require @-prefix; only Action is exempt.
        if (peek(1).type() == TokenType.L_PAREN
            && (!Builtins.isBuiltin(name) || name.equals("Action"))) {
          return parseComp(name);
        }
        adv();
        return new AstNode.Ref(name);
      }
      case BUILTIN_CALL -> {
        String name = tok.text();
        if (peek(1).type() == TokenType.L_PAREN) {
          return parseComp(name);
        }
        adv();
        return new AstNode.Ref(name);
      }
      case IDENT -> {
        adv();
        return new AstNode.Ref(tok.text());
      }
      case NOT -> {
        adv();
        return new AstNode.UnaryOp("!", parseExpr(PREC_UNARY));
      }
      case MINUS -> {
        adv();
        return new AstNode.UnaryOp("-", parseExpr(PREC_UNARY));
      }
      case L_PAREN -> {
        Token opener = adv(); // skip '('
        AstNode inner = parseExpr(0);
        eatClosing(TokenType.R_PAREN, ParseErrorCode.UNCLOSED_BRACKET, "'('", opener);
        return inner;
      }
      default -> {
        // Unknown token — record a diagnostic, skip it, and return Null (tolerant, matches TS).
        if (tok.type() != TokenType.EOF) {
          diagnostics.add(
              ParseDiagnostic.at(
                  ParseErrorCode.UNEXPECTED_TOKEN,
                  "Unexpected token " + tok.type(),
                  statementId,
                  new SourceSpan(tok.line(), tok.column(), tok.offset(),
                      tok.offset() + Math.max(0, tok.length()))));
        }
        adv();
        return new AstNode.Null();
      }
    }
  }

  // ── Infix / postfix ──────────────────────────────────────────────────────────
  private AstNode parseInfix(AstNode left) {
    Token tok = cur();
    switch (tok.type()) {
      case PLUS -> {
        adv();
        return new AstNode.BinOp("+", left, parseExpr(PREC_ADD));
      }
      case MINUS -> {
        adv();
        return new AstNode.BinOp("-", left, parseExpr(PREC_ADD));
      }
      case STAR -> {
        adv();
        return new AstNode.BinOp("*", left, parseExpr(PREC_MUL));
      }
      case SLASH -> {
        adv();
        return new AstNode.BinOp("/", left, parseExpr(PREC_MUL));
      }
      case PERCENT -> {
        adv();
        return new AstNode.BinOp("%", left, parseExpr(PREC_MUL));
      }
      case EQ_EQ -> {
        adv();
        return new AstNode.BinOp("==", left, parseExpr(PREC_EQ));
      }
      case NOT_EQ -> {
        adv();
        return new AstNode.BinOp("!=", left, parseExpr(PREC_EQ));
      }
      case GREATER -> {
        adv();
        return new AstNode.BinOp(">", left, parseExpr(PREC_CMP));
      }
      case LESS -> {
        adv();
        return new AstNode.BinOp("<", left, parseExpr(PREC_CMP));
      }
      case GREATER_EQ -> {
        adv();
        return new AstNode.BinOp(">=", left, parseExpr(PREC_CMP));
      }
      case LESS_EQ -> {
        adv();
        return new AstNode.BinOp("<=", left, parseExpr(PREC_CMP));
      }
      case AND -> {
        adv();
        return new AstNode.BinOp("&&", left, parseExpr(PREC_AND));
      }
      case OR -> {
        adv();
        return new AstNode.BinOp("||", left, parseExpr(PREC_OR));
      }
      case NULL_COAL -> {
        adv();
        return new AstNode.BinOp("??", left, parseExpr(PREC_OR));
      }
      case QUESTION -> {
        adv(); // consume '?'
        AstNode then = parseExpr(0);
        eatOptional(TokenType.COLON);
        AstNode els = parseExpr(0);
        return new AstNode.Ternary(left, then, els);
      }
      case DOT -> {
        adv(); // consume '.'
        return new AstNode.Member(left, readFieldName());
      }
      case L_BRACK -> {
        Token opener = adv(); // consume '['
        AstNode index = parseExpr(0);
        eatClosing(TokenType.R_BRACK, ParseErrorCode.UNCLOSED_BRACKET, "'['", opener);
        return new AstNode.Index(left, index);
      }
      default -> {
        return left; // should not be reached when infixPrec is correct
      }
    }
  }

  /** Read a member field name after a dot (mirrors expressions.ts member handling). */
  private String readFieldName() {
    Token f = cur();
    switch (f.type()) {
      case IDENT, TYPE -> {
        adv();
        return f.text();
      }
      case STR -> {
        adv();
        return f.text();
      }
      case NUM -> {
        adv();
        return numberText(f);
      }
      case STATE_VAR -> {
        adv();
        String v = f.text();
        return v.startsWith("$") ? v.substring(1) : v;
      }
      default -> {
        adv();
        return "?";
      }
    }
  }

  // ── Compound parsers ──────────────────────────────────────────────────────────
  private AstNode parseComp(String name) {
    adv(); // consume TYPE / BUILTIN_CALL name
    Token opener = cur();
    eatOptional(TokenType.L_PAREN);
    List<AstNode> args = new ArrayList<>();
    while (cur().type() != TokenType.R_PAREN && cur().type() != TokenType.EOF) {
      args.add(parseExpr(0));
      if (cur().type() == TokenType.COMMA) {
        adv();
      }
    }
    eatClosing(TokenType.R_PAREN, ParseErrorCode.UNCLOSED_BRACKET, "'('", opener);
    return new AstNode.Comp(name, args);
  }

  private AstNode parseArr() {
    Token opener = adv(); // skip '['
    List<AstNode> els = new ArrayList<>();
    while (cur().type() != TokenType.R_BRACK && cur().type() != TokenType.EOF) {
      els.add(parseExpr(0));
      if (cur().type() == TokenType.COMMA) {
        adv();
      }
    }
    eatClosing(TokenType.R_BRACK, ParseErrorCode.UNCLOSED_BRACKET, "'['", opener);
    return new AstNode.Arr(els);
  }

  private AstNode parseObj() {
    Token opener = adv(); // skip '{'
    List<AstNode.Obj.Entry> entries = new ArrayList<>();
    while (cur().type() != TokenType.R_BRACE && cur().type() != TokenType.EOF) {
      Token kt = cur();
      String key =
          switch (kt.type()) {
            case IDENT, STR, TYPE -> {
              adv();
              yield kt.text();
            }
            case NUM -> {
              adv();
              yield numberText(kt);
            }
            case STATE_VAR -> {
              adv();
              String v = kt.text();
              yield v.startsWith("$") ? v.substring(1) : v;
            }
            default -> {
              adv();
              yield "?";
            }
          };
      eatOptional(TokenType.COLON);
      entries.add(new AstNode.Obj.Entry(key, parseExpr(0)));
      if (cur().type() == TokenType.COMMA) {
        adv();
      }
    }
    eatClosing(TokenType.R_BRACE, ParseErrorCode.UNCLOSED_BRACKET, "'{'", opener);
    return new AstNode.Obj(entries);
  }

  /** Render a numeric token as JS-like {@code String(number)} for member/object keys. */
  private static String numberText(Token t) {
    double d = t.number();
    if (d == Math.rint(d) && !Double.isInfinite(d)) {
      return Long.toString((long) d);
    }
    return Double.toString(d);
  }
}
