package com.example.demo.architecture;

import com.example.demo.controller.CycleController;
import com.example.demo.controller.MissingRestAnnotationController;
import com.example.demo.controller.WrongName;
import com.example.demo.repository.AllowedRepositoryHelper;
import com.example.demo.repository.ClassBasedRepository;
import com.example.demo.repository.WrongSuffixSpringData;
import com.example.demo.repository.IndirectSpringData;
import com.example.demo.controller.MisplacedAdvice;
import com.example.demo.service.CycleService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingAndCycleContractTest {
    @Test
    void controller_conventions_detect_name_and_annotation_separately() {
        assertViolation(NamingConventionTest.controller_names, WrongName.class, "WrongName");
        assertViolation(NamingConventionTest.controller_annotations,
                MissingRestAnnotationController.class, "MissingRestAnnotationController");
    }

    @Test
    void only_spring_data_repositories_need_interface_and_suffix() {
        assertViolation(NamingConventionTest.spring_data_repositories_are_interfaces,
                ClassBasedRepository.class, "ClassBasedRepository");
        assertViolation(NamingConventionTest.spring_data_repositories_have_suffix,
                WrongSuffixSpringData.class, "WrongSuffixSpringData");
        assertViolation(NamingConventionTest.spring_data_repositories_have_suffix,
                IndirectSpringData.class, "IndirectSpringData");
        var classes = new ClassFileImporter().importClasses(AllowedRepositoryHelper.class);
        assertTrue(NamingConventionTest.spring_data_repositories_are_interfaces.allowEmptyShould(true).evaluate(classes)
                .getFailureReport().getDetails().isEmpty());
        assertTrue(NamingConventionTest.spring_data_repositories_have_suffix.allowEmptyShould(true).evaluate(classes)
                .getFailureReport().getDetails().isEmpty());
    }

    @Test
    void advice_and_core_cycle_are_detected() {
        assertViolation(NamingConventionTest.advices_reside_in_exception,
                MisplacedAdvice.class, "MisplacedAdvice");
        var cycle = new ClassFileImporter().importClasses(CycleController.class, CycleService.class);
        String report = String.join("\n", CodingRulesTest.coreCyclesFor("com.example.demo")
                .evaluate(cycle).getFailureReport().getDetails());
        assertTrue(report.contains("CycleController") && report.contains("CycleService"), report);
    }

    private static void assertViolation(ArchRule rule, Class<?> fixture, String expected) {
        String report = String.join("\n", rule.evaluate(new ClassFileImporter().importClasses(fixture))
                .getFailureReport().getDetails());
        assertTrue(report.contains(expected), report);
    }
}
