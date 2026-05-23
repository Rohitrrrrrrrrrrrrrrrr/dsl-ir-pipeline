package com.dslpipeline.pipeline;

import com.dslpipeline.model.dsl.ConditionNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Structured-Logic condition string into a {@link ConditionNode} tree.
 *
 * Supported grammar (depth-aware so parentheses and {@code [...]} lists are safe):
 *   expr  := orExpr
 *   orExpr  := andExpr ( 'OR'  andExpr )*
 *   andExpr := unary  ( 'AND' unary  )*
 *   unary   := 'NOT' unary | '(' expr ')' | leaf
 *   leaf    := termText OP rightText
 *   OP      := &lt; &lt;= &gt; &gt;= == != | 'in' | 'not in' | 'is missing' | 'is present'
 *
 * Leaf {@code left} text (which may be a function call) is preserved verbatim;
 * term parsing is deferred to the IR builder.
 *
 * @author Nikunj Malik
 */
public final class ConditionParser {

    private ConditionParser() {}

    public static ConditionNode parse(String condition) {
        if (condition == null || condition.isBlank()) {
            throw new ConditionParseException("empty condition");
        }
        return parseOr(condition.trim());
    }

    private static ConditionNode parseOr(String s) {
        List<String> parts = splitTopLevel(s, "OR");
        if (parts.size() == 1) return parseAnd(parts.get(0));
        List<ConditionNode> kids = new ArrayList<>();
        for (String p : parts) kids.add(parseAnd(p.trim()));
        return new ConditionNode.GroupCondition("OR", kids);
    }

    private static ConditionNode parseAnd(String s) {
        List<String> parts = splitTopLevel(s, "AND");
        if (parts.size() == 1) return parseUnary(parts.get(0));
        List<ConditionNode> kids = new ArrayList<>();
        for (String p : parts) kids.add(parseUnary(p.trim()));
        return new ConditionNode.GroupCondition("AND", kids);
    }

    private static ConditionNode parseUnary(String s) {
        String t = s.trim();
        if (t.regionMatches(true, 0, "NOT ", 0, 4)) {
            return new ConditionNode.NotCondition(parseUnary(t.substring(4).trim()));
        }
        if (t.startsWith("(") && t.endsWith(")") && balanced(t.substring(1, t.length() - 1))) {
            return parseOr(t.substring(1, t.length() - 1).trim());
        }
        return parseLeaf(t);
    }

    private static ConditionNode parseLeaf(String s) {
        String t = s.trim();
        // unary operators first
        for (String unary : new String[]{"is missing", "ismissing", "is present", "ispresent"}) {
            int idx = indexOfTopLevel(t, " " + unary);
            if (idx >= 0 && idx + unary.length() + 1 >= t.length()) {
                String left = t.substring(0, idx).trim();
                ConditionNode.LeafCondition leaf = new ConditionNode.LeafCondition();
                leaf.setLeft(left);
                leaf.setOp(unary.startsWith("is ") ? unary : (unary.equals("ismissing")
                        ? "is missing" : "is present"));
                leaf.setRight(null);
                return leaf;
            }
        }
        // 'not in' / 'in'
        int notInIdx = indexOfTopLevel(t, " not in ");
        if (notInIdx >= 0) {
            return listLeaf(t.substring(0, notInIdx), "not in", t.substring(notInIdx + 8));
        }
        int inIdx = indexOfTopLevel(t, " in ");
        if (inIdx >= 0) {
            return listLeaf(t.substring(0, inIdx), "in", t.substring(inIdx + 4));
        }
        // comparison operators (two-char first)
        for (String op : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
            int idx = indexOfTopLevel(t, op);
            if (idx > 0) {
                String left = t.substring(0, idx).trim();
                String right = t.substring(idx + op.length()).trim();
                ConditionNode.LeafCondition leaf = new ConditionNode.LeafCondition();
                leaf.setLeft(left);
                leaf.setOp(op);
                leaf.setRight(parseScalar(right));
                return leaf;
            }
        }
        throw new ConditionParseException("no comparison operator found in leaf: \"" + s + "\"");
    }

    private static ConditionNode listLeaf(String left, String op, String rightRaw) {
        ConditionNode.LeafCondition leaf = new ConditionNode.LeafCondition();
        leaf.setLeft(left.trim());
        leaf.setOp(op);
        leaf.setRight(parseList(rightRaw.trim()));
        return leaf;
    }

    // ─────────────────────────── value parsing ───────────────────────────

    static Object parseScalar(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        if (v.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (v.matches("[-+]?\\d+")) return Long.parseLong(v);
        if (v.matches("[-+]?\\d+\\.\\d+")) return new BigDecimal(v);
        // a bare word / path / function call — keep as string
        return v;
    }

    static List<Object> parseList(String raw) {
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
        else if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1);
        else if (t.startsWith("(") && t.endsWith(")")) t = t.substring(1, t.length() - 1);
        List<Object> out = new ArrayList<>();
        if (t.isBlank()) return out;
        for (String item : splitTopLevel(t, ",")) {
            out.add(parseScalar(item.trim()));
        }
        return out;
    }

    // ─────────────────────────── depth-aware utilities ───────────────────────────

    /** Split on a top-level keyword/char, respecting () and [] nesting. */
    private static List<String> splitTopLevel(String s, String sep) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        boolean keyword = sep.length() > 1 && Character.isLetter(sep.charAt(0));
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            if (depth == 0) {
                if (keyword) {
                    if (matchesKeyword(s, i, sep)) {
                        out.add(s.substring(start, i));
                        i += sep.length();
                        start = i;
                        continue;
                    }
                } else if (c == sep.charAt(0)) {
                    out.add(s.substring(start, i));
                    start = i + 1;
                    i++;
                    continue;
                }
            }
            i++;
        }
        out.add(s.substring(start));
        return out;
    }

    /** Index of a top-level substring (depth 0, case-insensitive), or -1. */
    private static int indexOfTopLevel(String s, String needle) {
        int depth = 0;
        for (int i = 0; i + needle.length() <= s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') { depth++; continue; }
            if (c == ')' || c == ']') { depth--; continue; }
            if (depth == 0 && s.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesKeyword(String s, int i, String kw) {
        // keyword must be surrounded by whitespace and case-insensitive
        if (!s.regionMatches(true, i, kw, 0, kw.length())) return false;
        boolean leftOk = i == 0 || Character.isWhitespace(s.charAt(i - 1));
        int after = i + kw.length();
        boolean rightOk = after >= s.length() || Character.isWhitespace(s.charAt(after));
        return leftOk && rightOk;
    }

    private static boolean balanced(String s) {
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            if (depth < 0) return false;
        }
        return depth == 0;
    }

    /** Thrown when a condition string cannot be parsed. */
    public static final class ConditionParseException extends RuntimeException {
        public ConditionParseException(String message) { super(message); }
    }
}
