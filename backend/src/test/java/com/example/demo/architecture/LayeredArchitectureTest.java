package com.example.demo.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    private static final String ROOT = "com.example.demo";
    private static final Set<String> CONTROLLER_MODELS = Set.of(
            ROOT + ".model.League", ROOT + ".model.Position");
    private static final Set<String> RESPONSE_DTOS_WITH_SERVICE_VIEWS = Set.of(
            ROOT + ".dto.quote.QuoteRunResponse",
            ROOT + ".dto.strategy.ActiveStrategyResponse",
            ROOT + ".dto.strategy.StrategyRef");
    private static final Set<String> SERVICE_VIEWS_USED_BY_RESPONSES = Set.of(
            ROOT + ".service.valuation.QuoteRunResult",
            ROOT + ".service.valuation.SkippedStrategy",
            ROOT + ".service.valuation.StrategyRunCounts",
            ROOT + ".service.valuation.ActiveStrategyView",
            ROOT + ".service.valuation.ActiveStrategyView$ChangedBy",
            ROOT + ".service.valuation.strategy.ValuationStrategyDefinition");

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy(ROOT + ".controller..")
            .layer("Service").definedBy(ROOT + ".service..")
            .layer("Repository").definedBy(ROOT + ".repository..")
            .layer("Adapter").definedBy(ROOT + ".adapter..")
            .layer("Model").definedBy(ROOT + ".model..")
            .layer("DTO").definedBy(ROOT + ".dto..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "DTO")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Model").mayOnlyBeAccessedByLayers(
                    "Controller", "Service", "Repository", "Adapter", "DTO")
            .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository")
            .because("Constitution §2.1 exige dependencias dirigidas entre capas del backend");

    @ArchTest
    static final ArchRule model_does_not_access_other_backend_packages = noClasses()
            .that().resideInAPackage(ROOT + ".model..")
            .should().dependOnClassesThat(DescribedPredicate.describe(
                    "another backend package", target -> target.getPackageName().startsWith(ROOT + ".")
                            && !inPackage(target.getPackageName(), ROOT + ".model")))
            .because("Constitution §2.1 mantiene model independiente de las otras capas y paquetes auxiliares");

    @ArchTest
    static final ArchRule controllers_use_only_allowed_backend_types = noClasses()
            .that().resideInAPackage(ROOT + ".controller..")
            .should().dependOnClassesThat(DescribedPredicate.describe(
                    "backend type outside service, dto, exception and the two allowed model enums",
                    target -> forbiddenControllerDependency(target, ROOT)))
            .because("Constitution §2.1 limita los controllers a contratos REST y a las excepciones de binding documentadas");

    @ArchTest
    static final ArchRule response_dtos_only_use_documented_service_views = noClasses()
            .that().resideInAPackage(ROOT + ".dto..")
            .should().dependOnClassesThat(DescribedPredicate.describe(
                    "service type other than the documented response views",
                    target -> inPackage(target.getPackageName(), ROOT + ".service")
                            && !SERVICE_VIEWS_USED_BY_RESPONSES.contains(target.getName())))
            .because("Constitution §2.1 sólo permite que los DTO de respuesta formen su salida desde vistas de service");

    @ArchTest
    static final ArchRule only_documented_response_dtos_depend_on_service = noClasses()
            .that(DescribedPredicate.describe("unapproved DTO",
                    origin -> inPackage(origin.getPackageName(), ROOT + ".dto")
                            && RESPONSE_DTOS_WITH_SERVICE_VIEWS.stream().noneMatch(approved ->
                                    origin.getName().equals(approved)
                                            || origin.getName().startsWith(approved + "$"))))
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".service..")
            .because("Constitution §2.1 acota la excepción dto a service a tres respuestas existentes");

    static boolean forbiddenControllerDependency(JavaClass target, String root) {
        String packageName = target.getPackageName();
        if (!packageName.startsWith(root + ".")) {
            return false;
        }
        // Los controllers sólo usan CorrelationIdFilter.HEADER para @Header(name=...) de OpenAPI.
        if (inPackage(packageName, root + ".service")
                || inPackage(packageName, root + ".dto")
                || inPackage(packageName, root + ".exception")
                || target.getName().equals(root + ".filter.CorrelationIdFilter")) {
            return false;
        }
        return !(inPackage(packageName, root + ".model")
                && CONTROLLER_MODELS.contains(target.getName()));
    }

    private static boolean inPackage(String actual, String expected) {
        return actual.equals(expected) || actual.startsWith(expected + ".");
    }
}
