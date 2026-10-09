plugins {
    id("liftlog.java-conventions")
    id("liftlog.quality-conventions")
    id("liftlog.testing-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "io.github.francisco3ferraz"
version = "0.0.1-SNAPSHOT"

springBoot {
    mainClass = "io.github.francisco3ferraz.liftlog.LiftLogApplication"
}

tasks.bootRun {
    systemProperty("user.timezone", "UTC")
    // Used only when no profile is active, so SPRING_PROFILES_ACTIVE still wins.
    systemProperty("spring.profiles.default", "local")
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.springdoc.openapi.webmvc.ui)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

val openApiSnapshot = rootProject.layout.projectDirectory.file("../api/openapi.json")

tasks.register<Test>("generateOpenApiDocs") {
    description = "Writes the OpenAPI document to api/openapi.json."
    group = "documentation"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("user.timezone", "UTC")
    systemProperty("liftlog.openapi.output", openApiSnapshot.asFile.absolutePath)
    useJUnitPlatform()
    filter { includeTestsMatching("*.OpenApiSnapshotTest") }
    outputs.file(openApiSnapshot)
    outputs.upToDateWhen { false }
}
