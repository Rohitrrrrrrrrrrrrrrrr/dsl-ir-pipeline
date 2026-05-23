package com.dslpipeline.model.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract Syntax Tree (AST) node — the parsed syntactic form of the DSL,
 * an intermediate stage between DSL (text) and IR (semantic).
 *
 * Each node carries:
 *   - kind: discriminator
 *   - attributes: per-kind data
 *   - children: nested AST nodes
 *
 * The AST is structurally identical to the DSL JSON tree but normalised
 * (e.g. operator aliases collapsed, type tags explicit on every node).
 *
 * @author Nikunj Malik
 */
public class AstNode {
    private String kind;          // RULE | CONDITION:leaf | CONDITION:group | ACTION:addError | etc.
    private java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
    private List<AstNode> children = new ArrayList<>();
    private SourceSpan sourceSpan;

    public AstNode() {}
    public AstNode(String kind) { this.kind = kind; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public java.util.Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(java.util.Map<String, Object> attributes) { this.attributes = attributes; }

    public List<AstNode> getChildren() { return children; }
    public void setChildren(List<AstNode> children) { this.children = children; }

    public SourceSpan getSourceSpan() { return sourceSpan; }
    public void setSourceSpan(SourceSpan sourceSpan) { this.sourceSpan = sourceSpan; }

    public AstNode attr(String k, Object v) { attributes.put(k, v); return this; }
    public AstNode child(AstNode c) { children.add(c); return this; }

    public static class SourceSpan {
        private int startLine;
        private int startCol;
        private int endLine;
        private int endCol;
        public SourceSpan() {}
        public SourceSpan(int sl, int sc, int el, int ec) {
            this.startLine = sl; this.startCol = sc; this.endLine = el; this.endCol = ec;
        }
        public int getStartLine() { return startLine; }
        public void setStartLine(int v) { this.startLine = v; }
        public int getStartCol() { return startCol; }
        public void setStartCol(int v) { this.startCol = v; }
        public int getEndLine() { return endLine; }
        public void setEndLine(int v) { this.endLine = v; }
        public int getEndCol() { return endCol; }
        public void setEndCol(int v) { this.endCol = v; }
    }
}
