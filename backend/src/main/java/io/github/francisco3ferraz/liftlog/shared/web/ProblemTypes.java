package io.github.francisco3ferraz.liftlog.shared.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable Problem Details {@code code}s and the {@code type} URI for each. Clients branch on these, so an existing code
 * never changes meaning. The URIs are URNs because they identify a problem type rather than locate documentation.
 */
public final class ProblemTypes {

    public static final String VALIDATION_FAILED = "validation-failed";
    public static final String INVALID_ID = "invalid-id";
    public static final String UNSUPPORTED_API_VERSION = "unsupported-api-version";
    public static final String INVALID_CURSOR = "invalid-cursor";
    public static final String PRECONDITION_REQUIRED = "precondition-required";
    public static final String PRECONDITION_FAILED = "precondition-failed";

    private static final String TYPE_PREFIX = "urn:problem-type:liftlog:";

    private ProblemTypes() {}

    /** The {@code type} URI for {@code code}. */
    public static URI type(String code) {
        return URI.create(TYPE_PREFIX + code);
    }

    /** The code for an error with no more specific one: the status name, e.g. {@code not-found} for 404. */
    static String forStatus(HttpStatusCode status) {
        var known = HttpStatus.resolve(status.value());
        return known == null
                ? "http-" + status.value()
                : known.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
