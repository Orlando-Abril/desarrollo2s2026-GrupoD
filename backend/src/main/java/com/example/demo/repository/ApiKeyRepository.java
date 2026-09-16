package com.example.demo.repository;

import com.example.demo.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a datos de {@link ApiKey}. Se busca por el hash de la key (autenticación
 * vía header X-API-KEY) y por el id del usuario dueño de la key.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByOwnerId(Long userId);
}
