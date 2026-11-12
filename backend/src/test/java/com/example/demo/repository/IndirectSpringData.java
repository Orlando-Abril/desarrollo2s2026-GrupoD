package com.example.demo.repository;

import org.springframework.data.repository.Repository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
interface FixtureRepositoryBase extends Repository<Object, Long> { }

@NoRepositoryBean
public interface IndirectSpringData extends FixtureRepositoryBase { }
