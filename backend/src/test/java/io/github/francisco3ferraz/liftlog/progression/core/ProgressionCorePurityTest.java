package io.github.francisco3ferraz.liftlog.progression.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ProgressionCorePurityTest {

    @Test
    void coreDependsOnlyOnTheJdkAndItself() {
        var core = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.francisco3ferraz.liftlog.progression.core");

        classes()
                .that()
                .resideInAPackage("..progression.core..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", "..progression.core..")
                .check(core);
    }
}
