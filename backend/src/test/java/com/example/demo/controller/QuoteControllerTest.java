package com.example.demo.controller;

import com.example.demo.exception.RecalculationFailedException;
import com.example.demo.exception.RecalculationInProgressException;
import com.example.demo.exception.UnknownValuationStrategyException;
import com.example.demo.model.Role;
import com.example.demo.repository.ApiKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.valuation.QuoteCalculationService;
import com.example.demo.service.valuation.QuoteRunResult;
import com.example.demo.service.valuation.SkippedStrategy;
import com.example.demo.service.valuation.StrategyRunCounts;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.UnitTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@UnitTestProfile
@Transactional
class QuoteControllerTest {

    private static final String PATH = "/quotes/recalculate";
    private static final UUID RUN_ID = UUID.fromString("7f1c2a9e-3b7d-4c51-9a3e-2d4f6b8c0e11");
    private static final QuoteRunResult RESULT = new QuoteRunResult(RUN_ID, Instant.parse("2026-10-23T18:00:00Z"),
            null, List.of(new StrategyRunCounts("OFFICIAL", 1, 2210, 270, 0)),
            List.of(new SkippedStrategy("MOMENTUM", 1, SkippedStrategy.NO_ALGORITHM)), 2480, 0, "OFFICIAL", true);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private QuoteCalculationService service;

    private String adminKey;

    @BeforeEach
    void setUp() {
        adminKey = AuthFixtures.apiKey(apiKeyRepository, AuthFixtures.user(userRepository, "admin", Role.ADMIN));
        when(service.recalculate(any())).thenReturn(RESULT);
    }

    @Test
    void withoutBodyRecalculatesEveryStrategy() throws Exception {
        mockMvc.perform(admin(post(PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.calculatedAt").value("2026-10-23T18:00:00Z"))
                .andExpect(jsonPath("$.requestedStrategy").doesNotExist())
                .andExpect(jsonPath("$.strategies[0].code").value("OFFICIAL"))
                .andExpect(jsonPath("$.strategies[0].version").value(1))
                .andExpect(jsonPath("$.strategies[0].calculated").value(2210))
                .andExpect(jsonPath("$.strategies[0].noStats").value(270))
                .andExpect(jsonPath("$.strategies[0].insufficientHistory").value(0))
                .andExpect(jsonPath("$.skippedStrategies[0].code").value("MOMENTUM"))
                .andExpect(jsonPath("$.skippedStrategies[0].version").value(1))
                .andExpect(jsonPath("$.skippedStrategies[0].reason").value("no_algorithm"))
                .andExpect(jsonPath("$.playersProcessed").value(2480))
                .andExpect(jsonPath("$.playersFailed").value(0))
                .andExpect(jsonPath("$.activeStrategy").value("OFFICIAL"))
                .andExpect(jsonPath("$.marketValueUpdated").value(true));
        verify(service).recalculate(Optional.empty());
    }

    @Test
    void emptyBodyOrNullStrategyRecalculatesEveryStrategy() throws Exception {
        mockMvc.perform(admin(json("{}"))).andExpect(status().isOk());
        mockMvc.perform(admin(json("{\"strategy\":null}"))).andExpect(status().isOk());

        verify(service, times(2)).recalculate(Optional.empty());
    }

    @Test
    void strategyInBodyIsPassedToTheService() throws Exception {
        mockMvc.perform(admin(json("{\"strategy\":\"OFFICIAL\"}"))).andExpect(status().isOk());

        verify(service).recalculate(Optional.of("OFFICIAL"));
    }

    @Test
    void unknownStrategyIs400() throws Exception {
        when(service.recalculate(Optional.of(""))).thenThrow(
                new UnknownValuationStrategyException("", List.of("OFFICIAL", "MOMENTUM")));

        mockMvc.perform(admin(json("{\"strategy\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_strategy"))
                .andExpect(jsonPath("$.message").value(containsString("OFFICIAL, MOMENTUM")));
    }

    @Test
    void invalidJsonIs400WithoutCallingTheService() throws Exception {
        mockMvc.perform(admin(json("{\"strategy\":")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(service);
    }

    @Test
    void runInProgressIs409() throws Exception {
        when(service.recalculate(any())).thenThrow(new RecalculationInProgressException());

        mockMvc.perform(admin(post(PATH)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("recalculation_in_progress"));
    }

    @Test
    void failedRunIs500WithoutInternalDetails() throws Exception {
        when(service.recalculate(any())).thenThrow(
                new RecalculationFailedException(RUN_ID, new IllegalStateException("connection refused")));

        mockMvc.perform(admin(post(PATH)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("recalculation_failed"))
                .andExpect(jsonPath("$.message").value("No se pudo completar el recálculo"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("X-API-KEY", adminKey);
    }

    private static MockHttpServletRequestBuilder json(String body) {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
