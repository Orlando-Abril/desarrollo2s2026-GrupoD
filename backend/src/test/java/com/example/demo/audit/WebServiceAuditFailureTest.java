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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Si armar la línea falla, el endpoint responde igual y sólo queda un aviso sin datos (FR-015). */
@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(locations = "classpath:audit-logging.properties")
@ExtendWith(OutputCaptureExtension.class)
class WebServiceAuditFailureTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @MockitoBean ParameterSanitizer sanitizer;

    @Test
    void auditFailureNeverBreaksTheRequest(CapturedOutput output) throws Exception {
        when(sanitizer.sanitize(any(), any())).thenThrow(new IllegalStateException("secreto-que-no-debe-salir"));
        User user = AuthFixtures.user(users, "audit-fail-" + UUID.randomUUID(), Role.USER);
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(user.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        assertThat(AuditLogLines.auditMessages(output, mark)).isEmpty();
        assertThat(AuditLogLines.since(output, mark)).singleElement().satisfies(line -> {
            assertThat(line.level()).isEqualTo("WARN");
            assertThat(line.message())
                    .isEqualTo("ws_audit_failed operation=PlayerController.list(..) error=IllegalStateException");
        });
        assertThat(output.getOut()).doesNotContain("secreto-que-no-debe-salir");
    }
}
