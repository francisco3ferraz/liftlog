package io.github.francisco3ferraz.liftlog.shared.web;

import io.github.francisco3ferraz.liftlog.shared.Ids;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id: the caller's {@code X-Request-Id} when it is well formed, otherwise a new UUIDv7. The id
 * is in the MDC under {@link #MDC_KEY} for the whole request, so logs and error responses carry it, and is echoed in
 * the response header. Runs before every other filter so that their logs carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Restrictive enough that a caller cannot forge log lines or inject into headers. */
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var incoming = request.getHeader(HEADER);
        var requestId = incoming != null && WELL_FORMED.matcher(incoming).matches()
                ? incoming
                : Ids.newV7().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
