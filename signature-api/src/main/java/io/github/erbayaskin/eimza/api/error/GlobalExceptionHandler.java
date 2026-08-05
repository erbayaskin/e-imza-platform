package io.github.erbayaskin.eimza.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setType(URI.create("https://errors.eimza.local/" + exception.code().toLowerCase()));
        problem.setTitle(exception.code());
        addCommonProperties(problem, request);
        problem.setProperty("code", exception.code());
        problem.setProperty("retryable", exception.retryable());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        var problem = ProblemDetail.forStatus(400);
        problem.setType(URI.create("https://errors.eimza.local/request-validation-failed"));
        problem.setTitle("İstek doğrulaması başarısız");
        problem.setDetail("İstek alanlarından biri veya daha fazlası geçersiz.");
        problem.setProperty("code", "REQUEST_VALIDATION_FAILED");
        problem.setProperty("retryable", false);
        problem.setProperty(
                "violations",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        Map.of(
                                                "field", error.getField(),
                                                "message", String.valueOf(error.getDefaultMessage())))
                        .toList());
        addCommonProperties(problem, request);
        return problem;
    }

    private void addCommonProperties(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", MDC.get("correlationId"));
        problem.setProperty("timestamp", Instant.now());
    }
}
