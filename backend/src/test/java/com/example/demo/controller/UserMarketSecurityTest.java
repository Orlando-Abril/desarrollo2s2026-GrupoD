package com.example.demo.controller;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class UserMarketSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApiKeyRepository apiKeys;
    @Autowired JwtUtil jwt;

    private User owner;
    private User other;
    private User admin;

    @BeforeEach
    void setUp() {
        owner = AuthFixtures.user(users, "portfolio-owner", Role.USER);
        other = AuthFixtures.user(users, "portfolio-other", Role.USER);
        admin = AuthFixtures.user(users, "portfolio-admin", Role.ADMIN);
    }

    @Test
    void bothEndpointsRejectMissingCredentialsWithoutLeakingData() throws Exception {
        assertNoLeak(mvc.perform(get(portfolio(owner.getId()))).andExpect(status().isUnauthorized()))
                .andExpect(jsonPath("$.error").value("unauthorized"));
        assertNoLeak(mvc.perform(get(transactions(owner.getId()))).andExpect(status().isUnauthorized()))
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void jwtOwnerCanReadBothEndpoints() throws Exception {
        mvc.perform(get(portfolio(owner.getId())).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(owner.getId()));
        mvc.perform(get(transactions(owner.getId())).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void userGetsTheSameForbiddenShapeForExistingAndMissingForeignIds() throws Exception {
        String token = bearer(owner);
        for (long target : new long[]{other.getId(), Long.MAX_VALUE}) {
            assertForbiddenWithoutLeak(mvc.perform(get(portfolio(target))
                    .header(HttpHeaders.AUTHORIZATION, token)));
            assertForbiddenWithoutLeak(mvc.perform(get(transactions(target))
                    .header(HttpHeaders.AUTHORIZATION, token)));
        }
    }

    @Test
    void adminCanReadExistingAccountsAndGetsNotFoundForMissingOnBothEndpoints() throws Exception {
        String token = bearer(admin);
        mvc.perform(get(portfolio(owner.getId())).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mvc.perform(get(transactions(owner.getId())).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        assertNoLeak(mvc.perform(get(portfolio(Long.MAX_VALUE)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())).andExpect(jsonPath("$.error").value("user_not_found"));
        assertNoLeak(mvc.perform(get(transactions(Long.MAX_VALUE)).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())).andExpect(jsonPath("$.error").value("user_not_found"));
    }

    @Test
    void ownerAndOwnerlessApiKeysFollowUserAndAdminRules() throws Exception {
        String ownerKey = AuthFixtures.apiKey(apiKeys, owner);
        String serviceKey = AuthFixtures.apiKey(apiKeys, null);
        mvc.perform(get(portfolio(owner.getId())).header("X-API-KEY", ownerKey)).andExpect(status().isOk());
        assertForbiddenWithoutLeak(mvc.perform(get(portfolio(other.getId())).header("X-API-KEY", ownerKey)));
        mvc.perform(get(portfolio(other.getId())).header("X-API-KEY", serviceKey)).andExpect(status().isOk());
        mvc.perform(get(transactions(other.getId())).header("X-API-KEY", serviceKey)).andExpect(status().isOk());
    }

    private void assertForbiddenWithoutLeak(ResultActions result) throws Exception {
        assertNoLeak(result.andExpect(status().isForbidden()))
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private ResultActions assertNoLeak(ResultActions result) throws Exception {
        return result.andExpect(jsonPath("$.balance").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.positions").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(user.getUsername());
    }

    private static String portfolio(long id) {
        return "/users/" + id + "/portfolio";
    }

    private static String transactions(long id) {
        return "/users/" + id + "/transactions";
    }
}
