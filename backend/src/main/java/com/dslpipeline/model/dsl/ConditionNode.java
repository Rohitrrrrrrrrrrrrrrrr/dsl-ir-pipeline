package com.dslpipeline.model.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Sealed condition-node hierarchy for the Rules DSL.
 * Type discriminator field is "type".
 *
 * @author Nikunj Malik
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "type", visible = true, defaultImpl = ConditionNode.LeafCondition.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConditionNode.LeafCondition.class, name = "leaf"),
        @JsonSubTypes.Type(value = ConditionNode.GroupCondition.class, name = "group"),
        @JsonSubTypes.Type(value = ConditionNode.NotCondition.class, name = "not"),
        @JsonSubTypes.Type(value = ConditionNode.MinusCondition.class, name = "minus"),
        @JsonSubTypes.Type(value = ConditionNode.ExistsCondition.class, name = "exists"),
        @JsonSubTypes.Type(value = ConditionNode.ForAllCondition.class, name = "forAll"),
        @JsonSubTypes.Type(value = ConditionNode.RuleRefCondition.class, name = "ruleRef"),
        @JsonSubTypes.Type(value = ConditionNode.CountWhereCondition.class, name = "countWhere"),
        @JsonSubTypes.Type(value = ConditionNode.DecisionTableCondition.class, name = "decisionTable")
})
public sealed interface ConditionNode permits
        ConditionNode.LeafCondition, ConditionNode.GroupCondition, ConditionNode.NotCondition,
        ConditionNode.MinusCondition, ConditionNode.ExistsCondition, ConditionNode.ForAllCondition,
        ConditionNode.RuleRefCondition, ConditionNode.CountWhereCondition,
        ConditionNode.DecisionTableCondition {

    final class LeafCondition implements ConditionNode {
        private String type = "leaf";
        private String left;
        private String op;
        private Object right;

        public LeafCondition() {}
        public LeafCondition(String left, String op, Object right) {
            this.left = left; this.op = op; this.right = right;
        }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLeft() { return left; }
        public void setLeft(String left) { this.left = left; }
        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }
        public Object getRight() { return right; }
        public void setRight(Object right) { this.right = right; }
    }

    final class GroupCondition implements ConditionNode {
        private String type = "group";
        private String operator; // AND | OR
        private List<ConditionNode> conditions;

        public GroupCondition() {}
        public GroupCondition(String operator, List<ConditionNode> conditions) {
            this.operator = operator; this.conditions = conditions;
        }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public List<ConditionNode> getConditions() { return conditions; }
        public void setConditions(List<ConditionNode> conditions) { this.conditions = conditions; }
    }

    final class NotCondition implements ConditionNode {
        private String type = "not";
        private ConditionNode condition;
        public NotCondition() {}
        public NotCondition(ConditionNode condition) { this.condition = condition; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public ConditionNode getCondition() { return condition; }
        public void setCondition(ConditionNode condition) { this.condition = condition; }
    }

    final class MinusCondition implements ConditionNode {
        private String type = "minus";
        private ConditionNode include;
        private List<ConditionNode> exclude;
        public MinusCondition() {}
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public ConditionNode getInclude() { return include; }
        public void setInclude(ConditionNode include) { this.include = include; }
        public List<ConditionNode> getExclude() { return exclude; }
        public void setExclude(List<ConditionNode> exclude) { this.exclude = exclude; }
    }

    final class ExistsCondition implements ConditionNode {
        private String type = "exists";
        private String collection;
        private String as;
        private ConditionNode condition;
        public ExistsCondition() {}
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getAs() { return as; }
        public void setAs(String as) { this.as = as; }
        public ConditionNode getCondition() { return condition; }
        public void setCondition(ConditionNode condition) { this.condition = condition; }
    }

    final class ForAllCondition implements ConditionNode {
        private String type = "forAll";
        private String collection;
        private String as;
        private ConditionNode condition;
        public ForAllCondition() {}
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getAs() { return as; }
        public void setAs(String as) { this.as = as; }
        public ConditionNode getCondition() { return condition; }
        public void setCondition(ConditionNode condition) { this.condition = condition; }
    }

    final class RuleRefCondition implements ConditionNode {
        private String type = "ruleRef";
        private String ruleId;
        public RuleRefCondition() {}
        public RuleRefCondition(String ruleId) { this.ruleId = ruleId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    }

    final class CountWhereCondition implements ConditionNode {
        private String type = "countWhere";
        private String collection;
        private String as;
        private ConditionNode condition;
        private String op;
        private Number value;
        public CountWhereCondition() {}
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getAs() { return as; }
        public void setAs(String as) { this.as = as; }
        public ConditionNode getCondition() { return condition; }
        public void setCondition(ConditionNode condition) { this.condition = condition; }
        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }
        public Number getValue() { return value; }
        public void setValue(Number value) { this.value = value; }
    }

    final class DecisionTableCondition implements ConditionNode {
        private String type = "decisionTable";
        private List<String> inputs;
        private List<DecisionRow> rows;
        private java.util.Map<String, Object> otherwise;
        public DecisionTableCondition() {}
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<String> getInputs() { return inputs; }
        public void setInputs(List<String> inputs) { this.inputs = inputs; }
        public List<DecisionRow> getRows() { return rows; }
        public void setRows(List<DecisionRow> rows) { this.rows = rows; }
        public java.util.Map<String, Object> getOtherwise() { return otherwise; }
        public void setOtherwise(java.util.Map<String, Object> otherwise) { this.otherwise = otherwise; }

        public static final class DecisionRow {
            private List<Object> when;
            private java.util.Map<String, Object> then;
            public DecisionRow() {}
            public List<Object> getWhen() { return when; }
            public void setWhen(List<Object> when) { this.when = when; }
            public java.util.Map<String, Object> getThen() { return then; }
            public void setThen(java.util.Map<String, Object> then) { this.then = then; }
        }
    }
}
