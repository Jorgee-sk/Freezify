package com.freezify.common;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CODE = "code";

    @ExceptionHandler(ApiException.class)
    @Nullable ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        body.setProperty(CODE, ex.code());
        return handleExceptionInternal(ex, body, new HttpHeaders(), ex.status(), request);
    }

    /** Two requests changed the same row at once; the loser can simply try again. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @Nullable ResponseEntity<Object> handleConcurrentChange(OptimisticLockingFailureException ex, WebRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The data was changed by someone else. Try again.");
        body.setProperty(CODE, "CONCURRENT_MODIFICATION");
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    @Nullable ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Unexpected error.");
        body.setProperty(CODE, "INTERNAL_ERROR");
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .toList();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
        body.setProperty(CODE, "VALIDATION_ERROR");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The file is too large.");
        body.setProperty(CODE, "FILE_TOO_LARGE");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Invalid query or path parameters get the same code as an invalid body. */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
        body.setProperty(CODE, "VALIDATION_ERROR");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Map<String, Object> properties = problem.getProperties();
            if (properties == null || !properties.containsKey(CODE)) {
                HttpStatus status = HttpStatus.resolve(statusCode.value());
                problem.setProperty(CODE, status == null ? "ERROR" : status.name());
            }
            problem.setProperty("correlationId", CorrelationIdFilter.current());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
