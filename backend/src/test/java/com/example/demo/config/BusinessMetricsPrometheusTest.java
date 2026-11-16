package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 036: las métricas de negocio salen por {@code /actuator/prometheus} con el acceso de la 035. */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.metrics.tags.application=demo"
})
class BusinessMetricsPrometheusTest {

    private static final Set<String> ALLOWED_LABELS =
            Set.of("application", "side", "type", "reason", "trigger", "outcome", "strategy", "source", "le");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;

    private String adminKey;
    private String userKey;

    @BeforeEach
    void setUp() {
        adminKey = AuthFixtures.apiKey(apiKeys, null);
        userKey = AuthFixtures.apiKey(apiKeys, AuthFixtures.user(users, "business-metrics-user", Role.USER));
    }

    @Test
    void adminApiKeySeesBusinessMetricsWithHelpAndApplicationTag() throws Exception {
        String text = scrape();

        assertThat(text.lines().filter(line -> line.startsWith("# HELP gdmarket_orders_placed_total ")))
                .singleElement().isEqualTo("# HELP gdmarket_orders_placed_total Órdenes de mercado aceptadas y confirmadas");
        assertThat(text.lines().filter(line -> line.startsWith("gdmarket_orders_placed_total{")))
                .hasSize(4)
                .anySatisfy(line -> assertThat(line)
                        .contains("application=\"demo\"", "side=\"BUY\"", "type=\"MARKET\""));
        assertThat(text).contains("gdmarket_users_registered{", "gdmarket_quotes_recalculation_last_seconds{",
                "gdmarket_quotes_recalculation_seconds_bucket{", "gdmarket_external_calls_total{");
    }

    @Test
    void businessSeriesOnlyUseTheDocumentedLabels() throws Exception {
        assertThat(scrape().lines().filter(line -> line.startsWith("gdmarket_"))).isNotEmpty()
                .allSatisfy(line -> {
                    Matcher matcher = LABEL.matcher(line);
                    while (matcher.find()) {
                        assertThat(ALLOWED_LABELS).as(line).contains(matcher.group(1));
                    }
                    assertThat(line).contains("application=\"demo\"");
                });
    }

    @Test
    void userApiKeyAndAnonymousCannotReadBusinessMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("X-API-KEY", userKey)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus").header("X-API-KEY", adminKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
