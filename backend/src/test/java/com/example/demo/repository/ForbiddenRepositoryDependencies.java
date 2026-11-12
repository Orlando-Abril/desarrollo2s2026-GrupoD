package com.example.demo.repository;

import com.example.demo.controller.ForbiddenControllerDependencies;
import com.example.demo.service.ForbiddenServiceDependencies;

public final class ForbiddenRepositoryDependencies {
    public static final class ServiceCase { ForbiddenServiceDependencies value; }
    public static final class ControllerCase { ForbiddenControllerDependencies value; }
}
