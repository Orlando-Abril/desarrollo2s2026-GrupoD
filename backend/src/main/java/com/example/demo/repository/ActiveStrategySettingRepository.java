package com.example.demo.repository;

import com.example.demo.model.ActiveStrategySetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ActiveStrategySettingRepository extends JpaRepository<ActiveStrategySetting, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ActiveStrategySetting s where s.id = :id")
    Optional<ActiveStrategySetting> findLockedById(@Param("id") Short id);
}
