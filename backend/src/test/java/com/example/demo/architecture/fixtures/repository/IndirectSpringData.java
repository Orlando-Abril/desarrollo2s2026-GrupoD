package com.example.demo.repository;

import org.springframework.data.repository.Repository;

interface FixtureRepositoryBase extends Repository<Object, Long> { }

public interface IndirectSpringData extends FixtureRepositoryBase { }
