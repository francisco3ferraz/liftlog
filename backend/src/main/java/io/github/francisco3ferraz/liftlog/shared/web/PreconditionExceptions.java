package io.github.francisco3ferraz.liftlog.shared.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The Problem Details errors for a write whose {@code If-Match} is missing or names a stale version. */
public final class PreconditionExceptions {

    private PreconditionExceptions() {}

    /** {@code 428 precondition-required}: the write did not say which version it was based on. */
    public static ErrorResponseException required() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_REQUIRED, "This request must send If-Match with the ETag it was based on.");
        problem.setProperty("code", ProblemTypes.PRECONDITION_REQUIRED);
        return new ErrorResponseException(HttpStatus.PRECONDITION_REQUIRED, problem, null);
    }

    /**
     * {@code 412 precondition-failed}: the resource changed since the client read it. The problem's {@code current}
     * property and the {@code ETag} header give the client what it needs to reconcile and retry without another read.
     */
    public static ErrorResponseException failed(String currentEtag, Object currentBody) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_FAILED, "The resource has changed since it was read.");
        problem.setProperty("code", ProblemTypes.PRECONDITION_FAILED);
        problem.setProperty("current", currentBody);
        var exception = new ErrorResponseException(HttpStatus.PRECONDITION_FAILED, problem, null);
        exception.getHeaders().set(HttpHeaders.ETAG, currentEtag);
        return exception;
    }
}
