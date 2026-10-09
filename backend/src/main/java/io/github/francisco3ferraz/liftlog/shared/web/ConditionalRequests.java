package io.github.francisco3ferraz.liftlog.shared.web;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Optimistic concurrency over HTTP. Reads send {@code ETag: W/"<version>"}; writes must send it back in
 * {@code If-Match}, and are refused if the entity has moved on since.
 */
public final class ConditionalRequests {

    private static final String WEAK_PREFIX = "W/";

    private ConditionalRequests() {}

    /** The weak entity tag for an entity at {@code version}. */
    public static String etag(long version) {
        return WEAK_PREFIX + quoted(version);
    }

    /**
     * Passes if {@code ifMatch} names {@code version}, comparing weakly so {@code "3"} and {@code W/"3"} are the same
     * tag. Throws {@code 428 precondition-required} when the header is absent, blank or {@code *}, since none of those
     * says which version the client saw; otherwise throws {@code 412 precondition-failed} carrying
     * {@code currentBody}.
     */
    public static void requireMatch(@Nullable String ifMatch, long version, Object currentBody) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            throw PreconditionExceptions.required();
        }
        var expected = quoted(version);
        var matches = Arrays.stream(ifMatch.split(","))
                .map(String::strip)
                .map(tag -> tag.startsWith(WEAK_PREFIX) ? tag.substring(WEAK_PREFIX.length()) : tag)
                .anyMatch(expected::equals);
        if (!matches) {
            throw PreconditionExceptions.failed(etag(version), currentBody);
        }
    }

    private static String quoted(long version) {
        return "\"" + version + "\"";
    }
}
