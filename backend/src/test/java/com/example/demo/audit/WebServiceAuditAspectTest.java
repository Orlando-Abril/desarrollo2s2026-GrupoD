package com.example.demo.audit;

import com.example.demo.controller.AuthController;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(locations = "classpath:audit-logging.properties")
@ExtendWith(OutputCaptureExtension.class)
class WebServiceAuditAspectTest {

    private static final String DURATION = ".* durationMs=\\d+ .*";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired JwtUtil jwt;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = AuthFixtures.user(users, "audit-" + UUID.randomUUID(), Role.USER);
        bearer = "Bearer " + jwt.generateToken(user.getUsername());
    }

    // US1

    @Test
    void jwtRequestLeavesOneLineWithAllFields(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        MvcResult result = mvc.perform(get("/players").param("league", "PREMIER_LEAGUE").param("position", "DEFENDER")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();

        List<AuditLogLines.Line> lines = AuditLogLines.since(output, mark);
        assertThat(lines).hasSize(1);
        AuditLogLines.Line line = lines.get(0);
        assertThat(line.timestamp()).isNotBlank();
        assertThat(line.level()).isEqualTo("INFO");
        assertThat(line.correlationId()).isEqualTo(result.getResponse().getHeader(CorrelationIdFilter.HEADER));
        assertThat(line.message())
                .startsWith("ws_audit user=" + user.getUsername() + " method=GET path=/players operation=PlayerController.list(..) ")
                .contains("params={\"league\":\"PREMIER_LEAGUE\",\"team\":null,\"position\":\"DEFENDER\"}")
                .matches(DURATION)
                .endsWith("outcome=OK");
    }

    @Test
    void apiKeyUserIsTheKeyOwner(CapturedOutput output) throws Exception {
        String key = AuthFixtures.apiKey(apiKeys, user);
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players/ranking").header("X-API-KEY", key)).andExpect(status().isOk());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .startsWith("ws_audit user=" + user.getUsername() + " method=GET path=/players/ranking "
                        + "operation=QuoteController.ranking(..) ")
                .endsWith("outcome=OK");
    }

    @Test
    void publicRequestIsAnonymous(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("reg-" + UUID.randomUUID(), "Registro!Secreto1")))
                .andExpect(status().isCreated());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .startsWith("ws_audit user=anonymous method=POST path=/auth/register operation=AuthController.register(..) ");
        assertThat(output.getOut()).doesNotContain("Registro!Secreto1");
    }

    @Test
    void portfolioLogsPathVariable(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/users/{id}/portfolio", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .contains("operation=UserMarketController.portfolio(..) ")
                .contains("\"id\":" + user.getId())
                .endsWith("outcome=OK");
    }

    @Test
    void authenticationArgumentIsOmitted(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/orders/mine").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .contains("operation=OrderController.")
                .contains(" params={} ")
                .endsWith("outcome=OK");
    }

    @Test
    void actuatorIsNotAudited(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/actuator/health"));

        assertThat(AuditLogLines.since(output, mark)).isEmpty();
    }

    // US2

    @Test
    void exceptionOutcomeKeepsTheOriginalResponse(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .contains("path=/players/999999 ")
                .contains("params={\"id\":999999}")
                .matches(DURATION)
                .endsWith("outcome=PlayerNotFoundException");
    }

    @Test
    void preAuthorizeDenialIsAudited(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(put("/strategies/active").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"MOMENTUM\"}"))
                .andExpect(status().isForbidden());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .startsWith("ws_audit user=" + user.getUsername() + " method=PUT path=/strategies/active "
                        + "operation=StrategyController.")
                .endsWith("outcome=AuthorizationDeniedException");
    }

    @Test
    void invalidRequestBodyNeverReachesTheMethod(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(AuditLogLines.since(output, mark)).isEmpty();
    }

    @Test
    void validatedControllerConstraintIsAudited(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/users/0/portfolio").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .endsWith("outcome=ConstraintViolationException");
    }

    @Test
    void linesNeverCarryExceptionDetails(CapturedOutput output) throws Exception {
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer));

        assertThat(AuditLogLines.auditMessages(output, mark)).allSatisfy(message ->
                assertThat(message).doesNotContain("Exception:", "at com."));
    }

    // US3

    @Test
    void loginPasswordIsMasked(CapturedOutput output) throws Exception {
        String username = "login-" + UUID.randomUUID();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, "S3creta!Audit")))
                .andExpect(status().isCreated());
        int mark = AuditLogLines.mark(output);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"S3creta!Audit\"}"))
                .andExpect(status().isOk());

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .startsWith("ws_audit user=anonymous method=POST path=/auth/login ")
                .contains("params={\"request\":{\"username\":\"" + username + "\",\"password\":\"***\"}}")
                .endsWith("outcome=OK");
        assertThat(output.getOut()).doesNotContain("S3creta!Audit");
    }

    @Test
    void refreshTokenCookieIsMasked(CapturedOutput output) throws Exception {
        String refreshToken = "refresh-" + UUID.randomUUID();
        int mark = AuditLogLines.mark(output);

        mvc.perform(post("/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, refreshToken)));

        assertThat(AuditLogLines.auditMessages(output, mark)).singleElement().asString()
                .contains("params={\"refreshToken\":\"***\"}");
        assertThat(output.getOut()).doesNotContain(refreshToken);
    }

    // US4

    @Test
    void lineAlsoGoesToTheAuditFile(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn();
        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);

        List<String> fileLines = Files.readAllLines(Path.of("logs", "audit.log"), StandardCharsets.UTF_8);
        assertThat(fileLines)
                .anySatisfy(line -> assertThat(line).contains("ws_audit ").contains(correlationId))
                .allSatisfy(line -> assertThat(line).contains("\"logger\":\"WEB_SERVICE_AUDIT\""));
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\""
                + password + "\"}";
    }
}
