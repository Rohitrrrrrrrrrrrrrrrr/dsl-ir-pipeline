package com.dslpipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone end-to-end NL → SL → DSL → AST → IR pipeline reference implementation.
 *
 * @author Nikunj Malik
 */
@SpringBootApplication
public class DslPipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DslPipelineApplication.class, args);
    }
}
