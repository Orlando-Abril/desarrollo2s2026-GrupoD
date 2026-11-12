package com.example.demo.adapter;

import com.example.demo.controller.ForbiddenControllerDependencies;
import com.example.demo.repository.ForbiddenRepositoryDependencies;
import com.example.demo.service.ForbiddenServiceDependencies;

public final class ForbiddenAdapterDependencies {
    public static final class ServiceCase { ForbiddenServiceDependencies value; }
    public static final class ControllerCase { ForbiddenControllerDependencies value; }
    public static final class RepositoryCase { ForbiddenRepositoryDependencies value; }
}
