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
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code audit.web-services.enabled=false} no registra el aspecto. */
@SpringBootTest(properties = "audit.web-services.enabled=false")
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
@TestPropertySource(locations = "classpath:audit-logging.properties")
@ExtendWith(OutputCaptureExtension.class)
class WebServiceAuditDisabledTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;

    @Test
    void disabledAspectWritesNothing(CapturedOutput output) throws Exception {
        assertThat(context.getBeansOfType(WebServiceAuditAspect.class)).isEmpty();
        User user = AuthFixtures.user(users, "audit-off-" + UUID.randomUUID(), Role.USER);
        int mark = AuditLogLines.mark(output);

        mvc.perform(get("/players").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(user.getUsername())))
                .andExpect(status().isOk());

        assertThat(AuditLogLines.since(output, mark)).isEmpty();
    }
}
