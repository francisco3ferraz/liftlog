package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the OpenAPI document to the path in the {@code liftlog.openapi.output} system property, which
 * {@code generateOpenApiDocs} points at the committed {@code api/openapi.json}. Keys are sorted so the file only
 * changes when the API does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSnapshotTest {

    private static final JsonMapper SORTED = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    MockMvcTester mvc;

    @Test
    void describesTheApiAndWritesTheSnapshot() throws IOException {
        var json = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(json).hasStatusOk();

        assertThat(json).bodyJson().isLenientlyEqualTo("""
                {
                  "openapi": "3.1.0",
                  "info": { "title": "LiftLog API", "version": "v1" },
                  "servers": [ { "url": "/" } ],
                  "security": [ { "session": [] } ],
                  "components": {
                    "securitySchemes": {
                      "session": { "type": "apiKey", "in": "cookie", "name": "LIFTLOG_SESSION" },
                      "csrf": { "type": "apiKey", "in": "header", "name": "X-XSRF-TOKEN" }
                    },
                    "schemas": {
                      "Problem": {
                        "required": [ "type", "title", "status", "code" ],
                        "properties": {
                          "type": { "format": "uri" },
                          "status": { "format": "int32" },
                          "errors": { "items": { "$ref": "#/components/schemas/InvalidField" } }
                        }
                      },
                      "InvalidField": { "required": [ "field", "message" ] }
                    }
                  }
                }
                """);

        Map<String, Object> document =
                SORTED.readValue(json.getResponse().getContentAsString(), new TypeReference<>() {});
        var output = Path.of(System.getProperty("liftlog.openapi.output", "build/openapi/openapi.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, SORTED.writeValueAsString(document) + "\n");
    }
}
