package com.example.demo.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("architecture-fixture-contract")
public class WrongName { }
