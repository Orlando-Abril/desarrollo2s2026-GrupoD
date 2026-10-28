package com.example.demo.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImmutableEntityConfigTest {

    @Test
    void makesBulkUpdatesOfImmutableEntitiesFail() {
        Map<String, Object> properties = new HashMap<>();

        new ImmutableEntityConfig().immutableEntityUpdateHandling().customize(properties);

        assertThat(properties).containsEntry(ImmutableEntityConfig.HANDLING_MODE, "exception");
    }
}
