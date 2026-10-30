package com.example.demo.service.market;

import com.example.demo.dto.market.PortfolioResponse;
import com.example.demo.dto.market.TransactionType;
import com.example.demo.exception.PortfolioInconsistentException;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.PortfolioHoldingRow;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.repository.market.UserTradeRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import com.example.demo.exception.UserNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock UserRepository users;
    @Mock HoldingRepository holdings;
    @Mock TradeRepository trades;
    @Mock MarketOrderRepository orders;
    @Mock SuperuserAccount superuser;

    private PortfolioService service;
    private User user;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = new PortfolioService(users, holdings, trades, orders, superuser);
        user = User.builder().id(7L).username("abril").role(Role.USER)
                .balance(new BigDecimal("900.00")).build();
        authentication = new UsernamePasswordAuthenticationToken("abril", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        lenient().when(users.findByUsername("abril")).thenReturn(Optional.of(user));
        lenient().when(users.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(superuser.username()).thenReturn("superuser");
    }

    @Test
    void weightedAverageSurvivesPartialSaleAndLaterPurchase() {
        when(orders.sumReservedCredits(7L)).thenReturn(new BigDecimal("12.0000"));
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, 10L, "Jugador", new BigDecimal("1.3000"), 13, 2)));
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(
                row(1, 10, 7, 99, 10, "1.0000"),
                row(2, 10, 7, 99, 5, "1.2000"),
                row(3, 10, 99, 7, 6, "1.5000"),
                row(4, 10, 7, 99, 4, "1.1000")));

        PortfolioResponse result = service.portfolio(7L, authentication);

        assertThat(result.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualTo(13);
            assertThat(position.availableQuantity()).isEqualTo(11);
            assertThat(position.averagePurchasePrice()).isEqualByComparingTo("1.0769");
            assertThat(position.costBasis()).isEqualByComparingTo("14.0000");
            assertThat(position.currentValue()).isEqualByComparingTo("16.9000");
            assertThat(position.profitLoss()).isEqualByComparingTo("2.9000");
        });
        assertThat(result.availableBalance()).isEqualByComparingTo("888.0000");
        assertThat(result.totalEquity()).isEqualByComparingTo("916.9000");
    }

    @Test
    void rejectsAReconstructedQuantityDifferentFromHolding() {
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, 10L, "Jugador", new BigDecimal("1.3000"), 9, 0)));
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(row(1, 10, 7, 99, 10, "1.0000")));

        assertThatThrownBy(() -> service.portfolio(7L, authentication))
                .isInstanceOf(PortfolioInconsistentException.class);
    }

    @Test
    void rejectsASaleLargerThanThePosition() {
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of());
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(row(1, 10, 99, 7, 1, "1.0000")));

        assertThatThrownBy(() -> service.portfolio(7L, authentication))
                .isInstanceOf(PortfolioInconsistentException.class);
    }

    @Test
    void aClosedPositionStartsANewAverageWhenBoughtAgain() {
        when(orders.sumReservedCredits(7L)).thenReturn(BigDecimal.ZERO);
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, 10L, "Jugador", new BigDecimal("1.0000"), 2, 0)));
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(
                row(1, 10, 7, 99, 5, "1.0000"), row(2, 10, 99, 7, 5, "1.2000"),
                row(3, 10, 7, 99, 2, "2.0000")));

        PortfolioResponse result = service.portfolio(7L, authentication);

        assertThat(result.positions().get(0).averagePurchasePrice()).isEqualByComparingTo("2.0000");
        assertThat(result.positions().get(0).profitLoss()).isEqualByComparingTo("-2.0000");
    }

    @Test
    void anEmptyPortfolioKeepsBalanceAsEquity() {
        when(orders.sumReservedCredits(7L)).thenReturn(BigDecimal.ZERO);
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of());
        when(trades.findCostBasisRows(7L)).thenReturn(List.of());

        PortfolioResponse result = service.portfolio(7L, authentication);

        assertThat(result.positions()).isEmpty();
        assertThat(result.totalEquity()).isEqualByComparingTo("900.0000");
    }

    @Test
    void superuserEmissionStartsAtOneCreditPerToken() {
        user.setUsername("superuser");
        when(orders.sumReservedCredits(7L)).thenReturn(BigDecimal.ZERO);
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, 10L, "Jugador", new BigDecimal("1.5000"), 90, 0)));
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(row(1, 10, 99, 7, 10, "1.2000")));

        PortfolioResponse result = service.portfolio(7L, authentication);

        assertThat(result.positions().get(0).averagePurchasePrice()).isEqualByComparingTo("1.0000");
        assertThat(result.positions().get(0).costBasis()).isEqualByComparingTo("90.0000");
    }

    @Test
    void missingMarketValueFailsExplicitly() {
        when(holdings.findPortfolioRows(7L)).thenReturn(List.of(
                new PortfolioHoldingRow(1L, 10L, "Jugador", null, 1, 0)));
        when(trades.findCostBasisRows(7L)).thenReturn(List.of(row(1, 10, 7, 99, 1, "1.0000")));

        assertThatThrownBy(() -> service.portfolio(7L, authentication))
                .isInstanceOf(PortfolioInconsistentException.class);
    }

    @Test
    void mapsBuyerAndSellerTradesWithStablePageMetadata() {
        UserTradeRow purchase = row(2, 10, 7, 99, 2, "1.2000");
        UserTradeRow sale = row(1, 10, 99, 7, 1, "1.1000");
        when(trades.findTransactionRows(eq(7L), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(purchase, sale), PageRequest.of(0, 20), 2));

        var result = service.transactions(7L, authentication, 0, 20);

        assertThat(result.items()).extracting(item -> item.type())
                .containsExactly(TransactionType.COMPRA, TransactionType.VENTA);
        assertThat(result.numberOfElements()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void userCannotProbeAnExistingOrMissingForeignId() {
        assertThatThrownBy(() -> service.portfolio(99L, authentication))
                .isInstanceOf(AccessDeniedException.class);
        verify(users, never()).findById(99L);

        assertThatThrownBy(() -> service.transactions(100L, authentication, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(users, never()).findById(100L);
    }

    @Test
    void adminAndOwnerlessServiceKeyCanReadAnyExistingTarget() {
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(trades.findTransactionRows(eq(7L), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.transactions(7L, admin, 0, 20).items()).isEmpty();
        verify(users, never()).findByUsername("admin");
    }

    @Test
    void adminGetsNotFoundOnlyAfterAuthorization() {
        Authentication admin = new UsernamePasswordAuthenticationToken("svc_key", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(users.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.portfolio(404L, admin))
                .isInstanceOf(UserNotFoundException.class);
        verify(users, never()).findByUsername("svc_key");
    }

    private UserTradeRow row(long id, long playerId, long buyerId, long sellerId, int quantity, String price) {
        BigDecimal value = new BigDecimal(price);
        return new UserTradeRow(id, playerId, "Jugador", buyerId, sellerId, quantity, value,
                value.multiply(BigDecimal.valueOf(quantity)), Instant.parse("2026-01-01T00:00:00Z").plusSeconds(id));
    }
}
