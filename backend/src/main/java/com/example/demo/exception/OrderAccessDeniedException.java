package com.example.demo.exception;

/** El usuario no puede operar con esa orden o no puede operar en el mercado (403); {@link #getCode()} lo distingue. */
public class OrderAccessDeniedException extends RuntimeException {

    public static final String ORDER_FORBIDDEN = "order_forbidden";
    public static final String SUPERUSER_NOT_ALLOWED = "superuser_not_allowed";
    public static final String USER_REQUIRED = "user_required";

    private final String code;

    public OrderAccessDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
