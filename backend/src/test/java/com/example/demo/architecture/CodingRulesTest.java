package com.example.demo.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)
class CodingRulesTest {
    private static final String ROOT = "com.example.demo";

    @ArchTest
    static final ArchRule controller_classes_are_not_transactional = noClasses()
            .that().resideInAPackage("..controller..")
            .should().beAnnotatedWith(Transactional.class)
            .because("Constitution §2.1 deja el límite transaccional en service");

    @ArchTest
    static final ArchRule controller_methods_are_not_transactional = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should().beAnnotatedWith(Transactional.class)
            .because("Constitution §2.1 deja el límite transaccional en service");

    @ArchTest
    static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("Constitution §5.3 exige usar el logger estructurado en vez de streams estándar");

    @ArchTest
    static void central_layers_have_no_cycles(JavaClasses ignored) {
        // Sólo estas cinco capas: service ↔ dto y controller ↔ exception son dependencias aceptadas.
        ArchRule rule = slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .because("Constitution §2.1 prohíbe ciclos entre controller, service, repository, adapter y model");
        JavaClasses central = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT + ".controller", ROOT + ".service", ROOT + ".repository",
                        ROOT + ".adapter", ROOT + ".model");
        rule.check(central);
    }
}
