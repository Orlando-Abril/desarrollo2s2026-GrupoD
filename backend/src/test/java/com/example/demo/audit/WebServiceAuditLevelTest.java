package com.example.demo.audit;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con {@code logging.level.WEB_SERVICE_AUDIT=WARN} no se escriben líneas y el endpoint funciona igual. */
@SpringBootTest(properties = "logging.level.WEB_SERVICE_AUDIT=WARN")
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(locations = "classpath:audit-logging.properties")
@ExtendWith(OutputCaptureExtension.class)
class WebServiceAuditLevelTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;

    @Test
    void levelAboveInfoSilencesTheAudit(CapturedOutput output) throws Exception {
        User user = AuthFixtures.user(users, "audit-warn-" + UUID.randomUUID(), Role.USER);
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(user.getUsername())))
                .andExpect(status().isOk());

        assertThat(AuditLogLines.since(output, mark)).isEmpty();
    }
}
