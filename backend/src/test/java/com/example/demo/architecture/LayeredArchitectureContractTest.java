package com.example.demo.architecture;

import com.example.demo.adapter.ForbiddenAdapterDependencies;
import com.example.demo.controller.AllowedControllerDependencies;
import com.example.demo.controller.ForbiddenControllerDependencies;
import com.example.demo.dto.ForbiddenDtoDependencies;
import com.example.demo.model.ForbiddenModelDependencies;
import com.example.demo.repository.ForbiddenRepositoryDependencies;
import com.example.demo.service.ForbiddenServiceDependencies;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredArchitectureContractTest {

    @Test
    void forbidden_directions_are_identified_in_isolated_fixtures() {
        ArchRule layers = LayeredArchitectureTest.layers;
        assertViolation(layers, ForbiddenControllerDependencies.RepositoryCase.class,
                "RepositoryCase", "ForbiddenRepositoryDependencies");
        assertViolation(layers, ForbiddenServiceDependencies.ControllerCase.class,
                "ControllerCase", "ForbiddenControllerDependencies");
        assertViolation(layers, ForbiddenRepositoryDependencies.ServiceCase.class,
                "ServiceCase", "ForbiddenServiceDependencies");
        assertViolation(layers, ForbiddenRepositoryDependencies.ControllerCase.class,
                "ControllerCase", "ForbiddenControllerDependencies");
        assertViolation(layers, ForbiddenAdapterDependencies.ServiceCase.class,
                "ServiceCase", "ForbiddenServiceDependencies");
        assertViolation(layers, ForbiddenAdapterDependencies.ControllerCase.class,
                "ControllerCase", "ForbiddenControllerDependencies");
        assertViolation(layers, ForbiddenAdapterDependencies.RepositoryCase.class,
                "RepositoryCase", "ForbiddenRepositoryDependencies");
        assertViolation(layers, ForbiddenModelDependencies.ServiceCase.class,
                "ServiceCase", "ForbiddenServiceDependencies");
        assertViolation(layers, ForbiddenModelDependencies.ControllerCase.class,
                "ControllerCase", "ForbiddenControllerDependencies");
        assertViolation(layers, ForbiddenModelDependencies.RepositoryCase.class,
                "RepositoryCase", "ForbiddenRepositoryDependencies");
        assertViolation(layers, ForbiddenModelDependencies.AdapterCase.class,
                "AdapterCase", "ForbiddenAdapterDependencies");
        assertViolation(layers, ForbiddenModelDependencies.DtoCase.class,
                "DtoCase", "ForbiddenDtoDependencies");
        assertViolation(LayeredArchitectureTest.model_does_not_access_other_backend_packages,
                ForbiddenModelDependencies.FilterCase.class, "FilterCase", "FixtureFilter");
    }

    @Test
    void controller_restrictions_are_narrow_and_allowed_enums_pass() {
        ArchRule controller = LayeredArchitectureTest.controllers_use_only_allowed_backend_types;
        assertViolation(controller, ForbiddenControllerDependencies.RepositoryCase.class,
                "RepositoryCase", "ForbiddenRepositoryDependencies");
        assertViolation(controller, ForbiddenControllerDependencies.AdapterCase.class,
                "AdapterCase", "ForbiddenAdapterDependencies");
        assertViolation(controller, ForbiddenControllerDependencies.ConfigCase.class,
                "ConfigCase", "SecurityConfig");
        assertViolation(controller, ForbiddenControllerDependencies.SecurityCase.class,
                "SecurityCase", "JwtUtil");
        assertViolation(controller, ForbiddenControllerDependencies.ModelCase.class,
                "ModelCase", "ForbiddenModelDependencies");
        assertViolation(controller, ForbiddenControllerDependencies.FilterCase.class,
                "FilterCase", "FixtureFilter");
        assertNoViolation(controller, AllowedControllerDependencies.class);
        assertNoViolation(controller, ForbiddenControllerDependencies.CorrelationFilterCase.class);
    }

    @Test
    void dto_exception_does_not_admit_other_dtos_or_service_types() {
        assertViolation(LayeredArchitectureTest.only_documented_response_dtos_depend_on_service,
                ForbiddenDtoDependencies.ServiceCase.class, "ServiceCase", "ForbiddenServiceDependencies");
        assertViolation(LayeredArchitectureTest.response_dtos_only_use_documented_service_views,
                ForbiddenDtoDependencies.ServiceCase.class, "ServiceCase", "ForbiddenServiceDependencies");
    }

    @Test
    void header_ast_accepts_documentation_and_ignores_comments_and_literals() {
        Path root = Path.of("src/test/java/com/example/demo/architecture/fixtures/controller");
        assertTrue(ControllerHeaderSourceCheck.violations(root.resolve("AllowedHeaderUsage.java")).isEmpty());
        var violations = ControllerHeaderSourceCheck.violations(root.resolve("HeaderUsageSamples.java"));
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("CorrelationIdFilter may only supply @Header"));
    }

    private static void assertViolation(ArchRule rule, Class<?> origin, String originName, String targetName) {
        var classes = rule == LayeredArchitectureTest.layers
                ? new ClassFileImporter().importClasses(origin,
                    ForbiddenControllerDependencies.class, ForbiddenServiceDependencies.class,
                    ForbiddenRepositoryDependencies.class, ForbiddenAdapterDependencies.class,
                    ForbiddenModelDependencies.class, ForbiddenDtoDependencies.class)
                : new ClassFileImporter().importClasses(origin);
        String report = String.join("\n", rule.evaluate(classes)
                .getFailureReport().getDetails());
        assertFalse(report.isEmpty(), origin.getName() + " should violate " + rule.getDescription());
        assertTrue(report.contains(originName) && report.contains(targetName), report);
    }

    private static void assertNoViolation(ArchRule rule, Class<?> origin) {
        String report = String.join("\n", rule.evaluate(new ClassFileImporter().importClasses(origin))
                .getFailureReport().getDetails());
        assertTrue(report.isEmpty(), report);
    }
}
