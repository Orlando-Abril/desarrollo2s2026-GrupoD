package com.example.demo.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.context.annotation.Profile;

@ControllerAdvice
@Profile("architecture-fixture-contract")
public class MisplacedAdvice { }
