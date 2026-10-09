package io.github.francisco3ferraz.liftlog.shared;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TimeConfigTest {

    @Test
    void clockIsUtc() {
        assertThat(new TimeConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void productionCodeNeverReadsTheSystemTimeDirectly() {
        var main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.github.francisco3ferraz.liftlog");

        noClasses()
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDate.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .orShould()
                .callMethod(OffsetDateTime.class, "now")
                .orShould()
                .callMethod(ZonedDateTime.class, "now")
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .because("time comes from the injected Clock so tests can fix it")
                .check(main);
    }
}
