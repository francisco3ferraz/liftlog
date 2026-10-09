package io.github.francisco3ferraz.liftlog.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document's metadata, its security schemes and the shared {@code Problem} schema. Every operation needs
 * the session cookie unless it declares otherwise; unsafe methods also send the CSRF cookie's value in a header. The
 * server URL is relative so the document is the same wherever it is generated.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String SESSION_SCHEME = "session";
    private static final String CSRF_SCHEME = "csrf";

    @Bean
    OpenAPI liftLogOpenApi() {
        return new OpenAPI()
                .info(new Info().title("LiftLog API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(
                                SESSION_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("LIFTLOG_SESSION"))
                        .addSecuritySchemes(
                                CSRF_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN"))
                        .addSchemas("Problem", problem())
                        .addSchemas("InvalidField", invalidField()));
    }

    private static Schema<?> problem() {
        return object("An RFC 9457 Problem Details body.")
                .required(List.of("type", "title", "status", "code"))
                .addProperty("type", string("Stable problem type URI.").format("uri"))
                .addProperty("title", string("Short summary of the problem type."))
                .addProperty("status", integer("HTTP status code."))
                .addProperty("detail", string("Explanation specific to this occurrence."))
                .addProperty(
                        "instance", string("Request path of this occurrence.").format("uri-reference"))
                .addProperty("code", string("Stable machine-readable code; clients branch on this."))
                .addProperty("requestId", string("Id of the request, as logged by the server."))
                .addProperty(
                        "errors",
                        typed("array", "One entry per invalid field, for validation failures.")
                                .items(new Schema<>().$ref("#/components/schemas/InvalidField")));
    }

    private static Schema<?> invalidField() {
        return object("One invalid field or parameter.")
                .required(List.of("field", "message"))
                .addProperty(
                        "field", string("Field or parameter name; empty when the error is about the whole object."))
                .addProperty("message", string("Why the value is invalid."));
    }

    private static Schema<?> object(String description) {
        return typed("object", description);
    }

    private static Schema<?> string(String description) {
        return typed("string", description);
    }

    private static Schema<?> integer(String description) {
        return typed("integer", description).format("int32");
    }

    private static Schema<?> typed(String type, String description) {
        return new Schema<>().types(Set.of(type)).description(description);
    }
}
