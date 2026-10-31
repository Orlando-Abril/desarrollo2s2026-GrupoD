package com.example.demo.exception;

import com.example.demo.controller.StrategyController;
import com.example.demo.controller.UserMarketController;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de estrategia (spec 031) fuera de {@code QuoteController}, con el mismo {@link ErrorResponse} que el resto
 * de la API. No maneja {@code AccessDeniedException} ni {@code Exception}: el 403 de {@code @PreAuthorize} lo sigue
 * respondiendo {@link GlobalExceptionHandler} (research R1).
 */
@RestControllerAdvice(assignableTypes = {UserMarketController.class, StrategyController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StrategyExceptionHandler {

    @ExceptionHandler(UnknownValuationStrategyException.class)
    public ResponseEntity<ErrorResponse> handleUnknownStrategy(UnknownValuationStrategyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("unknown_strategy", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation_error", "El cuerpo del pedido es inválido"));
    }
}
