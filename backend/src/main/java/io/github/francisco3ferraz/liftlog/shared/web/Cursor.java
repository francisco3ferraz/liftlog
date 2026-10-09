package io.github.francisco3ferraz.liftlog.shared.web;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A keyset position: the sort key and id of the last item on a page. Clients see it only as an opaque base64url string
 * and send it back unchanged to get the next page; one that does not decode is answered with {@code 400
 * invalid-cursor}.
 *
 * @param sortKey the last item's primary sort value, rendered as text by the query that pages on it
 * @param id the last item's id, which breaks ties between equal sort keys
 */
public record Cursor(String sortKey, UUID id) {

    private static final int MAX_ENCODED_LENGTH = 512;
    private static final int UUID_LENGTH = 36;
    private static final char SEPARATOR = ':';

    /** The opaque form handed to clients as {@code nextCursor}. */
    public String encode() {
        var payload = id.toString() + SEPARATOR + sortKey;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** Parses a client-supplied cursor, throwing a {@code 400 invalid-cursor} problem if it is not one we issued. */
    public static Cursor decode(String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw invalid();
        }
        try {
            var bytes = Base64.getUrlDecoder().decode(encoded);
            var payload = StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (payload.length() <= UUID_LENGTH || payload.charAt(UUID_LENGTH) != SEPARATOR) {
                throw invalid();
            }
            var idText = payload.substring(0, UUID_LENGTH);
            var id = UUID.fromString(idText);
            if (!id.toString().equals(idText)) {
                throw invalid();
            }
            return new Cursor(payload.substring(UUID_LENGTH + 1), id);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw invalid();
        }
    }

    private static ErrorResponseException invalid() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The cursor is not valid.");
        problem.setProperty("code", ProblemTypes.INVALID_CURSOR);
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
}
