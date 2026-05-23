package com.dslpipeline.term;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Recursive-descent parser for condition terms.
 *
 * Grammar:
 *   term     := number | string | boolean | call | path
 *   call     := IDENT ('.' IDENT)* '(' [ term (',' term)* ] ')'
 *   path     := IDENT ('.' IDENT)*
 *   number   := '-'? DIGITS ('.' DIGITS)?
 *   string   := '"' ... '"'  |  "'" ... "'"
 *
 * Special case: {@code dec("123.45")} is recognised as an explicit decimal literal.
 *
 * @author Nikunj Malik
 */
public final class TermParser {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final List<Tok> toks;
    private int pos;
    private final String src;

    private TermParser(String src) {
        this.src = src;
        this.toks = tokenize(src);
    }

    /** Parse a single term. Throws {@link TermParseException} on malformed input. */
    public static Term parse(String src) {
        if (src == null || src.isBlank()) {
            throw new TermParseException("empty term");
        }
        TermParser p = new TermParser(src);
        Term t = p.parseTerm();
        if (p.pos != p.toks.size()) {
            throw new TermParseException("unexpected trailing input near '" + p.peekRaw() + "' in: " + src);
        }
        return t;
    }

    /** Lenient probe — returns true if the string parses as exactly one term. */
    public static boolean isParseable(String src) {
        try { parse(src); return true; } catch (RuntimeException e) { return false; }
    }

    // ─────────────────────────── parsing ───────────────────────────

    private Term parseTerm() {
        Tok t = peek();
        if (t == null) throw new TermParseException("expected a term, found end of input: " + src);

        switch (t.kind) {
            case NUMBER -> { pos++; return numberLiteral(t.text); }
            case STRING -> { pos++; return stringLiteral(t.text); }
            case IDENT -> {
                // Could be boolean, a call, or a path. Read a (possibly dotted) name first.
                String name = parseDottedName();
                if (matches(TokKind.LPAREN)) {
                    return parseCall(name);
                }
                if (name.equalsIgnoreCase("true"))  return Term.LiteralTerm.bool(true);
                if (name.equalsIgnoreCase("false")) return Term.LiteralTerm.bool(false);
                return new Term.PathTerm(List.of(name.split("\\.")));
            }
            default -> throw new TermParseException("unexpected token '" + t.text + "' in: " + src);
        }
    }

    private Term parseCall(String name) {
        // 'dec(' "..." ')' → explicit decimal literal
        expect(TokKind.LPAREN);
        if ("dec".equalsIgnoreCase(name)) {
            Tok arg = peek();
            if (arg == null || arg.kind != TokKind.STRING) {
                throw new TermParseException("dec() requires a quoted decimal string, e.g. dec(\"100.00\")");
            }
            pos++;
            expect(TokKind.RPAREN);
            try {
                return Term.LiteralTerm.dec(new BigDecimal(arg.text));
            } catch (NumberFormatException e) {
                throw new TermParseException("invalid decimal literal: dec(\"" + arg.text + "\")");
            }
        }
        List<Term> args = new ArrayList<>();
        if (!matches(TokKind.RPAREN)) {
            args.add(parseTerm());
            while (matches(TokKind.COMMA)) {
                pos++;
                args.add(parseTerm());
            }
        }
        expect(TokKind.RPAREN);
        return new Term.CallTerm(name, args);
    }

    private String parseDottedName() {
        StringBuilder sb = new StringBuilder();
        Tok first = expect(TokKind.IDENT);
        sb.append(first.text);
        while (matches(TokKind.DOT)) {
            pos++;
            Tok seg = expect(TokKind.IDENT);
            sb.append('.').append(seg.text);
        }
        return sb.toString();
    }

    private Term numberLiteral(String text) {
        if (text.contains(".")) {
            return Term.LiteralTerm.dec(new BigDecimal(text));
        }
        return Term.LiteralTerm.num(Long.parseLong(text));
    }

    private Term stringLiteral(String text) {
        if (ISO_DATE.matcher(text).matches()) {
            return Term.LiteralTerm.date(text);
        }
        return Term.LiteralTerm.str(text);
    }

    // ─────────────────────────── token helpers ───────────────────────────

    private Tok peek() { return pos < toks.size() ? toks.get(pos) : null; }
    private String peekRaw() { Tok t = peek(); return t == null ? "<eof>" : t.text; }

    private boolean matches(TokKind kind) {
        Tok t = peek();
        return t != null && t.kind == kind;
    }

    private Tok expect(TokKind kind) {
        Tok t = peek();
        if (t == null || t.kind != kind) {
            throw new TermParseException("expected " + kind + " but found '" + peekRaw() + "' in: " + src);
        }
        pos++;
        return t;
    }

    // ─────────────────────────── tokenizer ───────────────────────────

    private enum TokKind { IDENT, NUMBER, STRING, LPAREN, RPAREN, COMMA, DOT }

    private record Tok(TokKind kind, String text) {}

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '(') { out.add(new Tok(TokKind.LPAREN, "(")); i++; continue; }
            if (c == ')') { out.add(new Tok(TokKind.RPAREN, ")")); i++; continue; }
            if (c == ',') { out.add(new Tok(TokKind.COMMA, ",")); i++; continue; }
            if (c == '"' || c == '\'') {
                char quote = c;
                int j = i + 1;
                StringBuilder sb = new StringBuilder();
                while (j < n && s.charAt(j) != quote) {
                    if (s.charAt(j) == '\\' && j + 1 < n) { sb.append(s.charAt(j + 1)); j += 2; }
                    else { sb.append(s.charAt(j)); j++; }
                }
                if (j >= n) throw new TermParseException("unterminated string literal in: " + s);
                out.add(new Tok(TokKind.STRING, sb.toString()));
                i = j + 1;
                continue;
            }
            if (c == '.') {
                // a dot is a path separator unless it is part of a number (handled below)
                out.add(new Tok(TokKind.DOT, "."));
                i++;
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))
                    || (c == '+' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                if (s.charAt(j) == '-' || s.charAt(j) == '+') j++;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
                if (j < n && s.charAt(j) == '.' && j + 1 < n && Character.isDigit(s.charAt(j + 1))) {
                    j++;
                    while (j < n && Character.isDigit(s.charAt(j))) j++;
                }
                out.add(new Tok(TokKind.NUMBER, s.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '$')) j++;
                out.add(new Tok(TokKind.IDENT, s.substring(i, j)));
                i = j;
                continue;
            }
            throw new TermParseException("unexpected character '" + c + "' in: " + s);
        }
        return out;
    }

    /** Thrown when a term string cannot be parsed. */
    public static final class TermParseException extends RuntimeException {
        public TermParseException(String message) { super(message); }
    }
}
