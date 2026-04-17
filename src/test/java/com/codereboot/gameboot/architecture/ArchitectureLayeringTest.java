package com.codereboot.gameboot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureLayeringTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.codereboot.gameboot");

    @Test
    void packageSlicesShouldBeAcyclic() {
        slices().matching("com.codereboot.gameboot.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    void domainShouldNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..application..", "..transport..", "..infra..", "..security..")
                .check(classes);
    }

    @Test
    void applicationShouldNotDependOnApiOrTransport() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..api..", "..transport..")
                .check(classes);
    }

    @Test
    void apiShouldNotDependOnTransport() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..transport..")
                .check(classes);
    }
}
