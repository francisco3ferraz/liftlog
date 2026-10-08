plugins {
    java
    jacoco
}

val libs = versionCatalogs.named("libs")

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

dependencies {
    // Jupiter and AssertJ versions come from the Spring Boot BOM.
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core")
    "testImplementation"(libs.findLibrary("jqwik").get())
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

// *IT classes run in the same `test` task. Filter with -PtestScope=unit or -PtestScope=integration.
val testScope = providers.gradleProperty("testScope").orElse("all")

tasks.test {
    // Instants are UTC; this also keeps legacy host zone ids (e.g. "Portugal") away from Postgres 18.
    systemProperty("user.timezone", "UTC")
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
    filter {
        when (val scope = testScope.get()) {
            "all" -> {}
            "unit" -> excludeTestsMatching("*IT")
            "integration" -> includeTestsMatching("*IT")
            else -> throw GradleException("Unknown testScope '$scope' (expected all, unit or integration)")
        }
        isFailOnNoMatchingTests = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

val coverageExclusions = listOf("**/LiftLogApplication.class")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExclusions) })
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExclusions) })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("io.github.francisco3ferraz.liftlog.progression.core")
            limit {
                counter = "BRANCH"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
