package com.example.demo.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingConventionTest {
    private static final String ROOT = "com.example.demo";

    @ArchTest
    static final ArchRule controller_names = classes().that(controllerTypes())
            .should().haveSimpleNameEndingWith("Controller")
            .because("Constitution §2.1 identifica los endpoints por el sufijo Controller");

    @ArchTest
    static final ArchRule controller_annotations = classes().that(controllerTypes())
            .should().beAnnotatedWith(RestController.class)
            .because("Constitution §2.1 exige declarar los endpoints REST como controllers");

    @ArchTest
    static final ArchRule spring_data_repositories_are_interfaces = classes().that(springDataTypes())
            .should().beInterfaces()
            .because("Constitution §2.1 limita la convención a repositorios de persistencia Spring Data");

    @ArchTest
    static final ArchRule spring_data_repositories_have_suffix = classes().that(springDataTypes())
            .should().haveSimpleNameEndingWith("Repository")
            .because("Constitution §2.1 identifica las interfaces Spring Data con el sufijo Repository");

    @ArchTest
    static final ArchRule advices_reside_in_exception = classes().that(DescribedPredicate.describe(
                    "controller advice", type -> type.isAnnotatedWith(ControllerAdvice.class)
                            || type.isAnnotatedWith(RestControllerAdvice.class)))
            .should().resideInAPackage("..exception..")
            .because("Constitution §2.1 ubica el manejo transversal de errores en exception");

    private static DescribedPredicate<JavaClass> controllerTypes() {
        return DescribedPredicate.describe("top level controller type", type ->
                type.getPackageName().startsWith(ROOT + ".controller")
                        && !type.getName().contains("$"));
    }

    private static DescribedPredicate<JavaClass> springDataTypes() {
        return DescribedPredicate.describe("Spring Data repository implementation or interface", type ->
                type.getPackageName().startsWith(ROOT + ".repository")
                        && type.isAssignableTo(Repository.class));
    }
}
