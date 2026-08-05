package io.github.erbayaskin.eimza.smartcard.error;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(AgentException.class)
    ResponseEntity<ErrorResponse> handleAgentException(AgentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(exception.code(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "İstek alanları geçersiz.", Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("AGENT_ERROR", "Yerel aracı işlemi tamamlayamadı.", Instant.now()));
    }

    record ErrorResponse(String code, String message, Instant timestamp) {}
}
