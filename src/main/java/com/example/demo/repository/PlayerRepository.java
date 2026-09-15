package com.example.demo.repository;

import com.example.demo.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Acceso a datos de {@link Player}. Extiende {@link JpaSpecificationExecutor}
 * para permitir el filtrado dinámico del catálogo (liga, equipo, posición, etc.)
 * mediante {@link org.springframework.data.jpa.domain.Specification}.
 */
public interface PlayerRepository extends JpaRepository<Player, Long>, JpaSpecificationExecutor<Player> {

    Optional<Player> findByExternalId(String externalId);
}
