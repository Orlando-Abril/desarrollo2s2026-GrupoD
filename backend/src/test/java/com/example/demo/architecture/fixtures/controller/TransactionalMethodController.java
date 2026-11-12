package com.example.demo.controller;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalMethodController {
    @Transactional
    public void handle() { }
}
