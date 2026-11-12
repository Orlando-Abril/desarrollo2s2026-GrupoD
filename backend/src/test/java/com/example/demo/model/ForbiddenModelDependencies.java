package com.example.demo.model;

import com.example.demo.service.ForbiddenServiceDependencies;
import com.example.demo.controller.ForbiddenControllerDependencies;
import com.example.demo.repository.ForbiddenRepositoryDependencies;
import com.example.demo.adapter.ForbiddenAdapterDependencies;
import com.example.demo.dto.ForbiddenDtoDependencies;
import com.example.demo.filter.FixtureFilter;

public final class ForbiddenModelDependencies {
    public static final class ServiceCase { ForbiddenServiceDependencies value; }
    public static final class ControllerCase { ForbiddenControllerDependencies value; }
    public static final class RepositoryCase { ForbiddenRepositoryDependencies value; }
    public static final class AdapterCase { ForbiddenAdapterDependencies value; }
    public static final class DtoCase { ForbiddenDtoDependencies value; }
    public static final class FilterCase { FixtureFilter value; }
}
