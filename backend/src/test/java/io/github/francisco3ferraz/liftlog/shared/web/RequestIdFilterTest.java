package io.github.francisco3ferraz.liftlog.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<@Nullable String> idSeenByChain = new AtomicReference<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"req-123", "0199c5e2-7d3a-7b4e-9f00-1a2b3c4d5e6f", "a.b_C-9"})
    void wellFormedIncomingIdIsUsedAndEchoed(String incoming) throws Exception {
        filter(requestWithId(incoming));

        assertThat(idSeenByChain.get()).isEqualTo(incoming);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(incoming);
    }

    @Test
    void missingIdIsGeneratedAsAUuidV7AndEchoed() throws Exception {
        filter(new MockHttpServletRequest());

        var generated = idSeenByChain.get();
        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated).version()).isEqualTo(7);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(generated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "line\nbreak", "quote\"", "ünïcode"})
    void malformedIncomingIdIsReplaced(String incoming) throws Exception {
        filter(requestWithId(incoming));

        assertThat(idSeenByChain.get()).isNotEqualTo(incoming);
        assertThat(UUID.fromString(idSeenByChain.get()).version()).isEqualTo(7);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(idSeenByChain.get());
    }

    @Test
    void longestAcceptedIdIs64Characters() throws Exception {
        var incoming = "x".repeat(64);

        filter(requestWithId(incoming));

        assertThat(idSeenByChain.get()).isEqualTo(incoming);
    }

    @Test
    void longerIdIsReplaced() throws Exception {
        var incoming = "x".repeat(65);

        filter(requestWithId(incoming));

        assertThat(idSeenByChain.get()).isNotEqualTo(incoming);
    }

    @Test
    void idIsRemovedFromTheMdcAfterTheRequest() throws Exception {
        filter(requestWithId("req-123"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void idIsRemovedFromTheMdcEvenWhenTheChainThrows() {
        assertThatThrownBy(() -> filter.doFilter(requestWithId("req-123"), response, (req, res) -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private void filter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, (req, res) -> idSeenByChain.set(MDC.get(RequestIdFilter.MDC_KEY)));
    }

    private static MockHttpServletRequest requestWithId(String id) {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, id);
        return request;
    }
}
