package com.example.demo.config;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hace fallar las modificaciones JPQL masivas sobre entidades {@code @Immutable} (spec 026, FR-020). Se
 * configura en código para no tocar {@code application.properties}. Es global: también protege a
 * {@code PlayerQuote} y {@code PlayerStatsSnapshot}. Los {@code DELETE} JPQL masivos y el SQL nativo
 * quedan fuera de esta garantía.
 */
@Configuration
public class ImmutableEntityConfig {

    static final String HANDLING_MODE = "hibernate.query.immutable_entity_update_query_handling_mode";

    @Bean
    HibernatePropertiesCustomizer immutableEntityUpdateHandling() {
        return properties -> properties.put(HANDLING_MODE, "exception");
    }
}
