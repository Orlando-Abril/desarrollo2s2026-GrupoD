package com.example.demo.exception;

/** La orden ya está ejecutada o cancelada (409). */
public class OrderClosedException extends RuntimeException {

    public OrderClosedException(Long orderId) {
        super("La orden " + orderId + " ya está cerrada");
    }
}
