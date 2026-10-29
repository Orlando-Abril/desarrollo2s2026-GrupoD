package com.example.demo.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("No existe la orden " + orderId);
    }
}
