package com.example.demo.exception;

import com.example.demo.controller.OrderController;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Errores de {@link OrderController} con el mismo {@link ErrorResponse} que el resto de la API (spec 027, research
 * D9). Está acotado a ese controller para no cambiar la respuesta de ningún otro endpoint.
 */
@RestControllerAdvice(assignableTypes = OrderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MarketExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketExceptionHandler.class);
    private static final String VALIDATION_ERROR = "validation_error";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return reject(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleInvalidParameter(Exception ex) {
        return reject(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Uno o más parámetros son inválidos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return reject(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "El cuerpo del pedido es inválido");
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(InvalidOrderException ex) {
        return reject(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(PriceOutOfBandException.class)
    public ResponseEntity<ErrorResponse> handleOutOfBand(PriceOutOfBandException ex) {
        return reject(HttpStatus.BAD_REQUEST, "price_out_of_band", ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return reject(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient_balance", ex.getMessage());
    }

    @ExceptionHandler(InsufficientTokensException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientTokens(InsufficientTokensException ex) {
        return reject(HttpStatus.UNPROCESSABLE_CONTENT, "insufficient_tokens", ex.getMessage());
    }

    @ExceptionHandler(PlayerNotQuotedException.class)
    public ResponseEntity<ErrorResponse> handleNotQuoted(PlayerNotQuotedException ex) {
        return reject(HttpStatus.UNPROCESSABLE_CONTENT, "player_not_quoted", ex.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlayerNotFound(PlayerNotFoundException ex) {
        return reject(HttpStatus.NOT_FOUND, "player_not_found", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return reject(HttpStatus.NOT_FOUND, "order_not_found", ex.getMessage());
    }

    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrderAccessDeniedException ex) {
        return reject(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(OrderClosedException.class)
    public ResponseEntity<ErrorResponse> handleClosed(OrderClosedException ex) {
        return reject(HttpStatus.CONFLICT, "order_closed", ex.getMessage());
    }

    private static ResponseEntity<ErrorResponse> reject(HttpStatus status, String code, String message) {
        log.info("market_order_rejected status={} reason={}", status.value(), code);
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
