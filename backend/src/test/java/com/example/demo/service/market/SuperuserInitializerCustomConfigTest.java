package com.example.demo.service.market;

import com.example.demo.repository.UserRepository;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@UnitTestProfile
@TestPropertySource(properties = {
        "market.superuser.enabled=true",
        "market.superuser.username=mm",
        "market.superuser.email=mm@local",
        "market.superuser.password=Secret.123"
})
@ExtendWith(OutputCaptureExtension.class)
class SuperuserInitializerCustomConfigTest {

    @Autowired private SuperuserInitializer initializer;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        MarketTestData.clean(jdbcTemplate);
    }

    @Test
    void createsTheConfiguredSuperuserWithoutWarning(CapturedOutput output) {
        initializer.run(null);

        assertThat(userRepository.findByUsername("mm")).get().satisfies(user -> {
            assertThat(user.getEmail()).isEqualTo("mm@local");
            assertThat(passwordEncoder.matches("Secret.123", user.getPasswordHash())).isTrue();
        });
        assertThat(output.getOut()).doesNotContain("market_superuser_default_password");
    }
}
