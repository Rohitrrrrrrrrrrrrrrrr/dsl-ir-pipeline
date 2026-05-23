package com.dslpipeline.model.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed DSL action hierarchy: addError, addWarning, collection.pushAtPath, +=, ensure.
 *
 * @author Nikunj Malik
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DslAction.AddErrorAction.class, name = "addError"),
        @JsonSubTypes.Type(value = DslAction.AddWarningAction.class, name = "addWarning"),
        @JsonSubTypes.Type(value = DslAction.PushAtPathAction.class, name = "collection.pushAtPath"),
        @JsonSubTypes.Type(value = DslAction.PushAtPathAction.class, name = "+="),
        @JsonSubTypes.Type(value = DslAction.EnsureAction.class, name = "ensure")
})
public sealed interface DslAction permits
        DslAction.AddErrorAction, DslAction.AddWarningAction,
        DslAction.PushAtPathAction, DslAction.EnsureAction {

    String type();

    final class AddErrorAction implements DslAction {
        private String type = "addError";
        private String code;
        private String message;
        private String description;

        public AddErrorAction() {}
        public AddErrorAction(String code, String message) { this.code = code; this.message = message; }

        @Override public String type() { return type; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    final class AddWarningAction implements DslAction {
        private String type = "addWarning";
        private String code;
        private String message;
        private String description;

        public AddWarningAction() {}
        public AddWarningAction(String code, String message) { this.code = code; this.message = message; }

        @Override public String type() { return type; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    final class PushAtPathAction implements DslAction {
        private String type = "collection.pushAtPath";
        private String path;
        private Object value;

        public PushAtPathAction() {}
        public PushAtPathAction(String path, Object value) { this.path = path; this.value = value; }

        @Override public String type() { return type; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    final class EnsureAction implements DslAction {
        private String type = "ensure";
        private String path;
        private Object value;

        public EnsureAction() {}
        public EnsureAction(String path, Object value) { this.path = path; this.value = value; }

        @Override public String type() { return type; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}
