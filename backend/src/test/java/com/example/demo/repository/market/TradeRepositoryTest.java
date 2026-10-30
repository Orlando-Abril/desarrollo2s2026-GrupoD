package com.example.demo.repository.market;

import com.example.demo.config.ImmutableEntityConfig;
import com.example.demo.model.Player;
import com.example.demo.model.User;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.Trade;
import com.example.demo.support.UnitTestProfile;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@UnitTestProfile
@Import(ImmutableEntityConfig.class)
class TradeRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired private TradeRepository repository;
    @Autowired private MarketOrderRepository orderRepository;
    @Autowired private TestEntityManager entityManager;

    private Trade trade(User buyer, User seller, Player player, Instant executedAt) {
        MarketOrder buy = orderRepository.save(MarketOrder.limit(buyer, player, OrderSide.BUY, 10,
                new BigDecimal("1.0437"), T0));
        MarketOrder sell = orderRepository.save(MarketOrder.limit(seller, player, OrderSide.SELL, 10,
                new BigDecimal("1.0437"), T0));
        Trade trade = repository.save(Trade.of(buy, sell, 3, new BigDecimal("1.0437"), executedAt));
        entityManager.flush();
        return trade;
    }

    private Trade trade() {
        return trade(MarketFixtures.user(entityManager), MarketFixtures.user(entityManager),
                MarketFixtures.player(entityManager), T0);
    }

    @Test
    void keepsEveryField() {
        Trade saved = trade();
        entityManager.clear();

        Trade reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(3);
        assertThat(reloaded.getPrice()).isEqualByComparingTo("1.0437");
        assertThat(reloaded.getTotal()).isEqualByComparingTo("3.1311");
        assertThat(reloaded.getExecutedAt()).isEqualTo(T0);
    }

    @Test
    void ignoresChangesToALoadedTrade() {
        Trade saved = trade();

        ReflectionTestUtils.setField(saved, "price", new BigDecimal("9.9999"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getPrice()).isEqualByComparingTo("1.0437");
    }

    @Test
    void rejectsBulkUpdates() {
        Trade saved = trade();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createQuery("update Trade t set t.price = :price")
                .setParameter("price", new BigDecimal("9.9999"))
                .executeUpdate())
                .hasMessageContaining("immutable");
        assertThat(priceInDatabase(saved)).isEqualByComparingTo("1.0437");
    }

    @Test
    void rejectsRemovingATrade() {
        Trade saved = trade();

        assertThatThrownBy(() -> entityManager.remove(saved))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(countTrades(saved)).isEqualTo(1);
    }

    @Test
    void offersNoDeleteOperation() {
        assertThat(Arrays.stream(TradeRepository.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete"));
    }

    @Test
    void findsTheTradesOfAUserAsBuyerAndSellerFromNewestToOldest() {
        User user = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);
        Trade asBuyer = trade(user, MarketFixtures.user(entityManager), player, T0);
        Trade asSeller = trade(MarketFixtures.user(entityManager), user, player, T0.plusSeconds(60));
        trade();

        assertThat(repository.findByUser(user.getId(), PageRequest.of(0, 10))).containsExactly(asSeller, asBuyer);
    }

    @Test
    void projectsAStablePageWithPlayerAndBothSides() {
        User user = MarketFixtures.user(entityManager);
        User other = MarketFixtures.user(entityManager);
        Player player = MarketFixtures.player(entityManager);
        Trade older = trade(user, other, player, T0);
        Trade newer = trade(other, user, player, T0.plusSeconds(60));

        var page = repository.findTransactionRows(user.getId(), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(newer.getId());
            assertThat(row.playerName()).isEqualTo(player.getFullName());
            assertThat(row.sellerId()).isEqualTo(user.getId());
        });
        assertThat(repository.findTransactionRows(user.getId(), PageRequest.of(1, 1)).getContent())
                .extracting(UserTradeRow::id).containsExactly(older.getId());
    }

    @Test
    void theDatabaseRejectsATradeWithTheSameBuyerAndSeller() {
        Trade saved = trade();

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
                        "UPDATE trades SET seller_id = buyer_id WHERE id = " + saved.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    private BigDecimal priceInDatabase(Trade trade) {
        return (BigDecimal) entityManager.getEntityManager()
                .createNativeQuery("SELECT price FROM trades WHERE id = " + trade.getId()).getSingleResult();
    }

    private long countTrades(Trade trade) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM trades WHERE id = " + trade.getId()).getSingleResult())
                .longValue();
    }
}
