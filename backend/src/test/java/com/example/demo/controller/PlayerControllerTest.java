package com.example.demo.controller;

import com.example.demo.dto.player.PlayerResponse;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.filter.CorrelationIdFilter;
import com.example.demo.model.League;
import com.example.demo.model.Position;
import com.example.demo.service.PlayerCatalogQueryService;
import com.example.demo.service.PlayerCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerControllerTest {
    private final PlayerCatalogQueryService service = mock(PlayerCatalogQueryService.class);
    private final PlayerCatalogService catalogService = mock(PlayerCatalogService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(validated(new PlayerController(service, catalogService)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new CorrelationIdFilter()).build();

    /** Replica el proxy de @Validated que Spring aplica en la app real y que standaloneSetup no crea. */
    private static Object validated(Object controller) {
        ProxyFactory factory = new ProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new MethodValidationInterceptor());
        return factory.getProxy();
    }

    @Test
    void acceptsCombinedFiltersAndReturnsCorrelationId() throws Exception {
        when(service.findPlayers(League.PREMIER_LEAGUE, "Arsenal FC", Position.FORWARD))
                .thenReturn(List.of(new PlayerResponse(1L, "3180", "Saka", "Arsenal FC",
                        League.PREMIER_LEAGUE, Set.of(Position.FORWARD), "England", 25,
                        new BigDecimal("1.00"))));
        mvc.perform(get("/players").param("league", "PREMIER_LEAGUE")
                        .param("team", "Arsenal FC").param("position", "FORWARD"))
                .andExpect(status().isOk()).andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$[0].externalId").value("3180"));
    }

    @Test
    void invalidEnumReturnsValidationError() throws Exception {
        mvc.perform(get("/players").param("league", "INVALID"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void blankTeamReturnsValidationError() throws Exception {
        mvc.perform(get("/players").param("team", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void emptyCatalogReturnsEmptyArray() throws Exception {
        when(service.findPlayers(null, null, null)).thenReturn(List.of());
        mvc.perform(get("/players"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void syncReturnsSummary() throws Exception {
        when(catalogService.synchronizeCatalog())
                .thenReturn(new PlayerCatalogService.SyncResult("PARTIAL_FAILURE", 2110, 1, 0));
        mvc.perform(post("/players/sync"))
                .andExpect(status().isOk()).andExpect(header().exists(CorrelationIdFilter.HEADER))
                .andExpect(jsonPath("$.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.processed").value(2110))
                .andExpect(jsonPath("$.failedLeagues").value(1))
                .andExpect(jsonPath("$.failedPlayers").value(0));
    }
}
