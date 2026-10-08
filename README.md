# liftlog

## Compatibility notes

Checked on 2026-10-08 against Spring Boot 4.1.1 and JDK 25 (Temurin 25 in CI, OpenJDK 25.0.4.1 locally).

- **springdoc-openapi 3.1.1** (`springdoc-openapi-starter-webmvc-ui`) works with Boot 4.1.1: the app starts, `/v3/api-docs` serves an OpenAPI 3.1.0 document and Swagger UI responds 200.
- **Error Prone 2.50.0 + NullAway 0.14.2** (Gradle plugin `net.ltgt.errorprone` 5.1.1) compile on JDK 25 without extra `--add-exports` flags, and NullAway in JSpecify mode fails the build on a `@Nullable` dereference.
