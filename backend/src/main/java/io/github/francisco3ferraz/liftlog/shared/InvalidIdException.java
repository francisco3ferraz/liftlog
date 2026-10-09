package io.github.francisco3ferraz.liftlog.shared;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A client-supplied id that is not a UUIDv7. Answered with {@code 400 invalid-id}. */
public final class InvalidIdException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    InvalidIdException(UUID id) {
        super(HttpStatus.BAD_REQUEST, problem(id), null);
    }

    private static ProblemDetail problem(UUID id) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Id " + id + " is not a UUIDv7.");
        problem.setProperty("code", "invalid-id");
        return problem;
    }
}
