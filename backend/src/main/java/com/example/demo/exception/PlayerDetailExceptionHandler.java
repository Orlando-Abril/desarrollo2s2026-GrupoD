package com.example.demo.exception;

import com.example.demo.controller.PlayerController;
import com.example.demo.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PlayerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlayerDetailExceptionHandler {

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlayerNotFound(PlayerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("player_not_found", ex.getMessage()));
    }

    @ExceptionHandler(InvalidQuoteQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidQuoteQueryException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation_error", ex.getMessage()));
    }
}
