package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiVersioningTest.TestController.class)
@Import(ApiVersioningTest.TestController.class)
class ApiVersioningTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void v1PathResolvesToVersion1() {
        assertThat(mvc.get().uri("/api/v1/test/ping")).hasStatusOk().hasBodyTextEqualTo("pong v1");
    }

    @Test
    void unsupportedVersionIsAProblem() {
        assertThat(mvc.get().uri("/api/v2/test/ping"))
                .hasStatus(400)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "type": "urn:problem-type:liftlog:unsupported-api-version",
                          "code": "unsupported-api-version",
                          "instance": "/api/v2/test/ping"
                        }
                        """);
    }

    @Test
    void malformedVersionIsAProblem() {
        assertThat(mvc.get().uri("/api/latest/test/ping"))
                .hasStatus(400)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "unsupported-api-version" }
                        """);
    }

    @Test
    void pathsOutsideTheApiAreNotVersioned() {
        assertThat(mvc.get().uri("/test/unversioned")).hasStatusOk().hasBodyTextEqualTo("unversioned");
    }

    @RestController
    static class TestController {

        @GetMapping(path = "/api/{version}/test/ping", version = "1")
        String ping() {
            return "pong v1";
        }

        @GetMapping("/test/unversioned")
        String unversioned() {
            return "unversioned";
        }
    }
}
