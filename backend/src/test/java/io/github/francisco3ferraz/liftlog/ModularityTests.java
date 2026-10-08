package io.github.francisco3ferraz.liftlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(LiftLogApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        var outputFolder = Path.of("build", "spring-modulith-docs");

        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder(outputFolder.toString()))
                .writeDocumentation();

        assertThat(outputFolder.resolve("components.puml")).exists();
    }
}
