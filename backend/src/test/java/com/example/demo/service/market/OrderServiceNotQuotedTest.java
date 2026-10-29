package com.example.demo.service.market;

import com.example.demo.dto.market.OrderRequest;
import com.example.demo.exception.PlayerNotQuotedException;
import com.example.demo.model.Player;
import com.example.demo.model.market.OrderType;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import com.example.demo.repository.market.TradeRepository;
import com.example.demo.service.audit.TransactionAuditService;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Validación defensiva de la spec 027: hoy {@code Player.marketValue} es obligatorio en la base, así que un jugador
 * sin cotización no se puede crear y el caso se prueba con mocks.
 */
class OrderServiceNotQuotedTest {

    private final MarketUserResolver resolver = mock(MarketUserResolver.class);
    private final MarketLocks locks = mock(MarketLocks.class);
    private final MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
    private final TransactionAuditService auditService = mock(TransactionAuditService.class);
    private final OrderService service = new OrderService(resolver, locks, new OrderMatchingEngineService(),
            orderRepository, mock(HoldingRepository.class), mock(TradeRepository.class), mock(PlayerRepository.class),
            auditService, Clock.systemUTC());

    @Test
    void anOrderForAPlayerWithoutMarketValueIsRejectedBeforeTouchingTheBook() {
        when(resolver.resolve("user")).thenReturn(new MarketPrincipal(1L, "user"));
        when(locks.lockPlayer(7L)).thenReturn(Player.builder().id(7L).build());

        assertThatThrownBy(() -> service.placeBuy("user", new OrderRequest(7L, 1, OrderType.MARKET, null)))
                .isInstanceOf(PlayerNotQuotedException.class);

        verify(locks).lockPlayer(7L);
        verifyNoInteractions(orderRepository, auditService);
    }
}
