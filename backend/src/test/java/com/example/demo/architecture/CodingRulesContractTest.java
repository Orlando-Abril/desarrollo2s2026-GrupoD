package com.example.demo.architecture;

import com.example.demo.controller.TransactionalClassController;
import com.example.demo.controller.TransactionalMethodController;
import com.example.demo.service.PrintStackTraceCall;
import com.example.demo.service.StandardErrCall;
import com.example.demo.service.StandardOutCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingRulesContractTest {
    @Test
    void class_transaction_is_detected() {
        assertViolation(CodingRulesTest.controller_classes_are_not_transactional,
                TransactionalClassController.class, "TransactionalClassController");
    }

    @Test
    void method_transaction_is_detected() {
        assertViolation(CodingRulesTest.controller_methods_are_not_transactional,
                TransactionalMethodController.class, "TransactionalMethodController.handle");
    }

    @Test
    void each_standard_stream_or_stack_trace_is_detected() {
        assertViolation(CodingRulesTest.no_standard_streams, StandardOutCall.class, "StandardOutCall");
        assertViolation(CodingRulesTest.no_standard_streams, StandardErrCall.class, "StandardErrCall");
        assertViolation(CodingRulesTest.no_standard_streams, PrintStackTraceCall.class, "PrintStackTraceCall");
    }

    private static void assertViolation(ArchRule rule, Class<?> fixture, String expected) {
        String report = String.join("\n", rule.evaluate(new ClassFileImporter().importClasses(fixture))
                .getFailureReport().getDetails());
        assertTrue(report.contains(expected), report);
    }
}
