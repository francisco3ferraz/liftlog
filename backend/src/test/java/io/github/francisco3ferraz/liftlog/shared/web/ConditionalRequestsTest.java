package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class ConditionalRequestsTest {

    private static final Map<String, Object> CURRENT = Map.of("id", "0199c8a4", "reps", 9);

    @Nested
    class Etag {

        @Test
        void isAWeakTagOfTheVersion() {
            assertThat(ConditionalRequests.etag(0)).isEqualTo("W/\"0\"");
            assertThat(ConditionalRequests.etag(42)).isEqualTo("W/\"42\"");
        }
    }

    @Nested
    class Match {

        @Test
        void theTagIssuedForTheCurrentVersionMatches() {
            assertThatNoException()
                    .isThrownBy(() -> ConditionalRequests.requireMatch(ConditionalRequests.etag(3), 3, CURRENT));
        }

        @Test
        void comparisonIsWeakSoAStrongFormOfTheTagAlsoMatches() {
            assertThatNoException().isThrownBy(() -> ConditionalRequests.requireMatch("\"3\"", 3, CURRENT));
        }

        @Test
        void anyTagInAListCanMatch() {
            assertThatNoException().isThrownBy(() -> ConditionalRequests.requireMatch("W/\"1\", W/\"3\"", 3, CURRENT));
        }
    }

    @Nested
    class MissingPrecondition {

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "*"})
        void isAPreconditionRequiredProblem(String ifMatch) {
            assertPreconditionRequired(ifMatch);
        }

        @Test
        void anAbsentHeaderIsAPreconditionRequiredProblem() {
            assertPreconditionRequired(null);
        }

        private static void assertPreconditionRequired(@Nullable String ifMatch) {
            assertThatExceptionOfType(ErrorResponseException.class)
                    .isThrownBy(() -> ConditionalRequests.requireMatch(ifMatch, 3, CURRENT))
                    .satisfies(e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
                        assertThat(e.getBody().getProperties())
                                .containsEntry("code", "precondition-required")
                                .doesNotContainKey("current");
                    });
        }
    }

    @Nested
    class StaleVersion {

        @ParameterizedTest
        @ValueSource(strings = {"W/\"2\"", "\"4\"", "W/\"1\", W/\"2\"", "3", "W/\"03\"", "garbage"})
        void isAPreconditionFailedProblem(String ifMatch) {
            assertThatExceptionOfType(ErrorResponseException.class)
                    .isThrownBy(() -> ConditionalRequests.requireMatch(ifMatch, 3, CURRENT))
                    .satisfies(e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                        assertThat(e.getBody().getProperties()).containsEntry("code", "precondition-failed");
                    });
        }

        @Test
        void carriesTheCurrentRepresentationAndItsTag() {
            assertThatExceptionOfType(ErrorResponseException.class)
                    .isThrownBy(() -> ConditionalRequests.requireMatch("W/\"2\"", 3, CURRENT))
                    .satisfies(e -> {
                        assertThat(e.getBody().getProperties()).containsEntry("current", CURRENT);
                        assertThat(e.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("W/\"3\"");
                    });
        }
    }
}
