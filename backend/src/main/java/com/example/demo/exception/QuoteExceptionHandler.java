package com.example.demo.exception;

import com.example.demo.controller.QuoteController;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de {@link QuoteController} con el mismo {@link ErrorResponse} que el resto de la API. Está acotado a ese
 * controller para no cambiar la respuesta de ningún otro endpoint.
 */
@RestControllerAdvice(assignableTypes = QuoteController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QuoteExceptionHandler {

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

    @ExceptionHandler(RecalculationInProgressException.class)
    public ResponseEntity<ErrorResponse> handleInProgress(RecalculationInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("recalculation_in_progress", ex.getMessage()));
    }

    @ExceptionHandler(RecalculationFailedException.class)
    public ResponseEntity<ErrorResponse> handleFailed(RecalculationFailedException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("recalculation_failed", ex.getMessage()));
    }
}
