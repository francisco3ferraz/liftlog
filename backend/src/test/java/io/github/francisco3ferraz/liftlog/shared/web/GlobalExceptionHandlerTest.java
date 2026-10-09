package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.shared.Ids;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The handler is tested against a request id it finds in the MDC, so the filter that would replace it is left out.
@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RequestIdFilter.class))
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvcTester mvc;

    @BeforeEach
    void setRequestId() {
        MDC.put("requestId", "req-123");
    }

    @AfterEach
    void clearRequestId() {
        MDC.clear();
    }

    @Test
    void invalidBodyIsAValidationProblemListingEachFieldError() {
        var result = mvc.post()
                .uri("/test/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": " ", "reps": 0 }
                        """);

        assertThat(result)
                .hasStatus(400)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "type": "urn:problem-type:liftlog:validation-failed",
                          "status": 400,
                          "code": "validation-failed",
                          "instance": "/test/widgets",
                          "requestId": "req-123",
                          "errors": [
                            { "field": "name", "message": "must not be blank" },
                            { "field": "reps", "message": "must be greater than or equal to 1" }
                          ]
                        }
                        """);
    }

    @Test
    void invalidRequestParameterIsAValidationProblemNamingTheParameter() {
        assertThat(mvc.get().uri("/test/widgets?limit=101"))
                .hasStatus(400)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "validation-failed",
                          "errors": [ { "field": "limit", "message": "must be less than or equal to 100" } ]
                        }
                        """);
    }

    @Test
    void anErrorResponseExceptionKeepsItsCodeAndGetsTheMatchingType() {
        assertThat(mvc.get().uri("/test/widgets/9b2f3c1e-4d5a-4e6f-8a7b-1c2d3e4f5a6b"))
                .hasStatus(400)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "type": "urn:problem-type:liftlog:invalid-id",
                          "code": "invalid-id",
                          "instance": "/test/widgets/9b2f3c1e-4d5a-4e6f-8a7b-1c2d3e4f5a6b",
                          "requestId": "req-123"
                        }
                        """);
    }

    @Test
    void malformedJsonIsABadRequestProblem() {
        var result = mvc.post()
                .uri("/test/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json");

        assertThat(result).hasStatus(400).bodyJson().isLenientlyEqualTo("""
                        { "type": "urn:problem-type:liftlog:bad-request", "code": "bad-request" }
                        """);
    }

    @Test
    void unknownPathIsANotFoundProblem() {
        assertThat(mvc.get().uri("/test/nothing-here"))
                .hasStatus(404)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "not-found", "instance": "/test/nothing-here", "requestId": "req-123" }
                        """);
    }

    @Test
    void unexpectedExceptionIsAGenericServerErrorProblem() {
        assertThat(mvc.get().uri("/test/boom"))
                .hasStatus(500)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "type": "urn:problem-type:liftlog:internal-server-error",
                          "code": "internal-server-error",
                          "detail": "An unexpected error occurred.",
                          "instance": "/test/boom",
                          "requestId": "req-123"
                        }
                        """);
    }

    @Test
    void unexpectedExceptionLeaksNeitherItsMessageNorItsStackTrace() {
        assertThat(mvc.get().uri("/test/boom"))
                .bodyText()
                .doesNotContain("hunter2", "IllegalStateException", "TestController", "trace");
    }

    @Test
    void problemOmitsTheRequestIdWhenThereIsNone() {
        MDC.clear();

        assertThat(mvc.get().uri("/test/boom")).bodyJson().doesNotHavePath("$.requestId");
    }

    record Widget(@NotBlank String name, @Min(1) int reps) {}

    @RestController
    static class TestController {

        @PostMapping("/test/widgets")
        Widget create(@Valid @RequestBody Widget widget) {
            return widget;
        }

        @GetMapping("/test/widgets")
        String list(@RequestParam @Max(100) int limit) {
            return "limit " + limit;
        }

        @GetMapping("/test/widgets/{id}")
        UUID get(@PathVariable UUID id) {
            return Ids.requireV7(id);
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("password is hunter2");
        }
    }
}
