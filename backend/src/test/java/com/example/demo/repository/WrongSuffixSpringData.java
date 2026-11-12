package com.example.demo.repository;

import org.springframework.data.repository.Repository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface WrongSuffixSpringData extends Repository<Object, Long> { }
