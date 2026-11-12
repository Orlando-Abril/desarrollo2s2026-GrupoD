package com.example.demo.controller;

import com.example.demo.adapter.ForbiddenAdapterDependencies;
import com.example.demo.config.SecurityConfig;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.filter.FixtureFilter;
import com.example.demo.model.ForbiddenModelDependencies;
import com.example.demo.repository.ForbiddenRepositoryDependencies;
import com.example.demo.security.JwtUtil;

public final class ForbiddenControllerDependencies {
    public static final class RepositoryCase { ForbiddenRepositoryDependencies value; }
    public static final class AdapterCase { ForbiddenAdapterDependencies value; }
    public static final class ConfigCase { SecurityConfig value; }
    public static final class SecurityCase { JwtUtil value; }
    public static final class ModelCase { ForbiddenModelDependencies value; }
    public static final class FilterCase { FixtureFilter value; }
    public static final class CorrelationFilterCase { CorrelationIdFilter value; }
}
