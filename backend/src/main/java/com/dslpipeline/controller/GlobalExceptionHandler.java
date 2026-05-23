package com.dslpipeline.controller;

import com.dslpipeline.executor.IrExecutor;
import com.dslpipeline.extensions.CoreExtensions;
import com.dslpipeline.pipeline.ConditionParser;
import com.dslpipeline.term.TermEvaluator;
import com.dslpipeline.term.TermParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps pipeline exceptions to clean 400 responses so authoring mistakes are
 * surfaced as actionable diagnostics rather than 500 stack traces.
 *
 * @author Nikunj Malik
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            ConditionParser.ConditionParseException.class,
            TermParser.TermParseException.class,
            TermEvaluator.EvaluationException.class,
            TermEvaluator.NotConstantException.class,
            IrExecutor.ExecutionException.class,
            CoreExtensions.ExtensionException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled pipeline error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, Exception ex) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("error", status.getReasonPhrase());
        b.put("type", ex.getClass().getSimpleName());
        b.put("message", ex.getMessage());
        return ResponseEntity.status(status).body(b);
    }
}
