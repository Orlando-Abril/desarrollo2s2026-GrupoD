package com.example.demo.controller;

import com.example.demo.dto.market.PortfolioPositionResponse;
import com.example.demo.dto.market.PortfolioResponse;
import com.example.demo.dto.market.TransactionPageResponse;
import com.example.demo.dto.market.TransactionResponse;
import com.example.demo.dto.market.TransactionType;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.exception.PortfolioInconsistentException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.service.market.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserMarketControllerTest {

    @Mock PortfolioService service;
    private MockMvc mvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserMarketController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        authentication = new UsernamePasswordAuthenticationToken("abril", null, List.of());
    }

    @Test
    void returnsACompletePortfolioWithFourDecimalValues() throws Exception {
        PortfolioPositionResponse position = new PortfolioPositionResponse(10L, "Jugador", 3, 1, 2,
                bd("1.1000"), bd("3.3000"), bd("1.3000"), bd("3.9000"), bd("0.6000"));
        when(service.portfolio(7L, authentication)).thenReturn(new PortfolioResponse(7L, bd("900"), bd("12"),
                bd("888"), List.of(position), bd("3.3"), bd("3.9"), bd("0.6"), bd("903.9")));

        mvc.perform(get("/users/7/portfolio").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.balance").value(900.0000))
                .andExpect(jsonPath("$.reservedCredits").value(12.0000))
                .andExpect(jsonPath("$.availableBalance").value(888.0000))
                .andExpect(jsonPath("$.positions[0].playerId").value(10))
                .andExpect(jsonPath("$.positions[0].playerName").value("Jugador"))
                .andExpect(jsonPath("$.positions[0].quantity").value(3))
                .andExpect(jsonPath("$.positions[0].reservedQuantity").value(1))
                .andExpect(jsonPath("$.positions[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.positions[0].averagePurchasePrice").value(1.1000))
                .andExpect(jsonPath("$.positions[0].costBasis").value(3.3000))
                .andExpect(jsonPath("$.positions[0].marketValue").value(1.3000))
                .andExpect(jsonPath("$.positions[0].currentValue").value(3.9000))
                .andExpect(jsonPath("$.positions[0].profitLoss").value(0.6000))
                .andExpect(jsonPath("$.totalCost").value(3.3000))
                .andExpect(jsonPath("$.totalCurrentValue").value(3.9000))
                .andExpect(jsonPath("$.totalProfitLoss").value(0.6000))
                .andExpect(jsonPath("$.totalEquity").value(903.9000));
    }

    @Test
    void returnsAnEmptyPortfolio() throws Exception {
        when(service.portfolio(7L, authentication)).thenReturn(new PortfolioResponse(7L, bd("900"), bd("0"),
                bd("900"), List.of(), bd("0"), bd("0"), bd("0"), bd("900")));

        mvc.perform(get("/users/7/portfolio").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isEmpty())
                .andExpect(jsonPath("$.totalCurrentValue").value(0));
    }

    @Test
    void hidesInternalDetailsOfAnInconsistentPortfolio() throws Exception {
        when(service.portfolio(7L, authentication))
                .thenThrow(new PortfolioInconsistentException("sensitive database detail"));

        mvc.perform(get("/users/7/portfolio").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("portfolio_inconsistent"))
                .andExpect(jsonPath("$.message").value("No se pudo construir un portfolio consistente"));
    }

    @Test
    void returnsAnEmptyTransactionPageWithDefaults() throws Exception {
        when(service.transactions(7L, authentication, 0, 20)).thenReturn(
                new TransactionPageResponse(List.of(), 0, 20, 0, 0, 0, true, true));

        mvc.perform(get("/users/7/transactions").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void returnsPurchasesAndSalesWithPageMetadata() throws Exception {
        List<TransactionResponse> items = List.of(
                transaction(2, TransactionType.COMPRA), transaction(1, TransactionType.VENTA));
        when(service.transactions(7L, authentication, 1, 2)).thenReturn(
                new TransactionPageResponse(items, 1, 2, 2, 4, 2, false, true));

        mvc.perform(get("/users/7/transactions?page=1&size=2").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("COMPRA"))
                .andExpect(jsonPath("$.items[1].type").value("VENTA"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void invalidPaginationUsesTheCommonErrorShape() throws Exception {
        when(service.transactions(7L, authentication, -1, 20))
                .thenThrow(new IllegalArgumentException("invalid pagination"));
        when(service.transactions(7L, authentication, 0, 0))
                .thenThrow(new IllegalArgumentException("invalid pagination"));
        when(service.transactions(7L, authentication, 0, 101))
                .thenThrow(new IllegalArgumentException("invalid pagination"));

        mvc.perform(get("/users/7/transactions?page=-1").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mvc.perform(get("/users/7/transactions?size=0").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mvc.perform(get("/users/7/transactions?size=101").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void returnsTwentyThenFiveOfTwentyFiveTradesAndAnEmptyLaterPage() throws Exception {
        List<TransactionResponse> first = IntStream.iterate(25, id -> id >= 6, id -> id - 1)
                .mapToObj(id -> transaction(id, TransactionType.COMPRA)).toList();
        List<TransactionResponse> second = IntStream.iterate(5, id -> id >= 1, id -> id - 1)
                .mapToObj(id -> transaction(id, TransactionType.VENTA)).toList();
        when(service.transactions(7L, authentication, 0, 20)).thenReturn(
                new TransactionPageResponse(first, 0, 20, 20, 25, 2, true, false));
        when(service.transactions(7L, authentication, 1, 20)).thenReturn(
                new TransactionPageResponse(second, 1, 20, 5, 25, 2, false, true));
        when(service.transactions(7L, authentication, 2, 20)).thenReturn(
                new TransactionPageResponse(List.of(), 2, 20, 0, 25, 2, false, true));

        mvc.perform(get("/users/7/transactions?page=0&size=20").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/users/7/transactions?page=1&size=20").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/users/7/transactions?page=2&size=20").principal(authentication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(25));
    }

    @Test
    void traversesOneThousandTradesWithoutDuplicateIds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Set<Long> ids = new HashSet<>();
        for (int page = 0; page < 50; page++) {
            int currentPage = page;
            List<TransactionResponse> items = IntStream.range(0, 20)
                    .mapToObj(index -> transaction(1000L - currentPage * 20L - index, TransactionType.COMPRA))
                    .toList();
            when(service.transactions(7L, authentication, page, 20)).thenReturn(
                    new TransactionPageResponse(items, page, 20, 20, 1000, 50, page == 0, page == 49));

            String json = mvc.perform(get("/users/7/transactions?page=" + page + "&size=20")
                            .principal(authentication))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            mapper.readTree(json).get("items").forEach(item -> ids.add(item.get("id").asLong()));
        }
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(1000);
    }

    @Test
    void forbiddenAndNotFoundResponsesDoNotLeakAccountData() throws Exception {
        when(service.portfolio(8L, authentication)).thenThrow(new AccessDeniedException("foreign account"));
        mvc.perform(get("/users/8/portfolio").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.balance").doesNotExist())
                .andExpect(jsonPath("$.positions").doesNotExist());

        when(service.transactions(404L, authentication, 0, 20)).thenThrow(new UserNotFoundException());
        mvc.perform(get("/users/404/transactions").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("user_not_found"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    private static TransactionResponse transaction(long id, TransactionType type) {
        return new TransactionResponse(id, type, 10L, "Jugador", 2, bd("1.2000"), bd("2.4000"),
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(id));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
