package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class);

    @Test
    void exposesOneUtcClock() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
    }
}
