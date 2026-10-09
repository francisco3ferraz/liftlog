package io.github.francisco3ferraz.liftlog.shared.web;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 Problem Details response that carries a {@code code}, the matching {@code type},
 * the {@code requestId} and, for validation failures, one {@code errors[]} entry per invalid field.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String REQUEST_ID_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** One invalid field or parameter; {@code field} is empty when the error is about the whole object. */
    record InvalidField(String field, String message) {}

    @ExceptionHandler
    @Nullable
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        var problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getAllErrors().stream()
                .map(error -> invalidField(error, error instanceof FieldError f ? f.getField() : null))
                .toList();
        return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> invalidField(
                                error,
                                error instanceof FieldError f
                                        ? f.getField()
                                        : result.getMethodParameter().getParameterName())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            complete(problem, statusCode);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static void complete(ProblemDetail problem, HttpStatusCode status) {
        var properties = problem.getProperties();
        var code =
                properties != null && properties.get("code") instanceof String c ? c : ProblemTypes.forStatus(status);
        problem.setProperty("code", code);
        if (problem.getType() == null) {
            problem.setType(ProblemTypes.type(code));
        }
        var requestId = MDC.get(REQUEST_ID_KEY);
        if (requestId != null) {
            problem.setProperty(REQUEST_ID_KEY, requestId);
        }
    }

    private static ProblemDetail validationProblem(ProblemDetail problem, List<InvalidField> errors) {
        problem.setProperty("code", ProblemTypes.VALIDATION_FAILED);
        problem.setProperty(
                "errors",
                errors.stream()
                        .sorted(Comparator.comparing(InvalidField::field).thenComparing(InvalidField::message))
                        .toList());
        return problem;
    }

    private static InvalidField invalidField(MessageSourceResolvable error, @Nullable String field) {
        return new InvalidField(
                Objects.requireNonNullElse(field, ""),
                Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"));
    }
}
