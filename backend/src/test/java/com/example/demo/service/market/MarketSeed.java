package com.example.demo.service.market;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.market.Holding;
import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.OrderSide;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.market.HoldingRepository;
import com.example.demo.repository.market.MarketOrderRepository;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Siembra el estado del mercado con los repositorios, sin pasar por el motor ni dejar auditoría (spec 027). Las
 * órdenes reciben fechas de creación crecientes, así el orden de siembra es el orden de antigüedad en el libro.
 */
public final class MarketSeed {

    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00Z");

    private final PlayerRepository players;
    private final UserRepository users;
    private final HoldingRepository holdings;
    private final MarketOrderRepository orders;
    private final SuperuserAccount superuserAccount;
    private long tick;

    private MarketSeed(ApplicationContext context) {
        this.players = context.getBean(PlayerRepository.class);
        this.users = context.getBean(UserRepository.class);
        this.holdings = context.getBean(HoldingRepository.class);
        this.orders = context.getBean(MarketOrderRepository.class);
        this.superuserAccount = context.getBean(SuperuserAccount.class);
    }

    public static MarketSeed from(ApplicationContext context) {
        return new MarketSeed(context);
    }

    public Player player(String marketValue) {
        return players.save(Player.builder()
                .externalId("027-" + UUID.randomUUID())
                .fullName("Jugador 027")
                .team("Equipo 027")
                .league(League.PREMIER_LEAGUE)
                .marketValue(new BigDecimal(marketValue))
                .build());
    }

    public User user(String username, String balance) {
        return users.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("not-used")
                .role(Role.USER)
                .balance(new BigDecimal(balance))
                .build());
    }

    /** El superusuario con el username configurado, para que el mercado lo reconozca como tal. */
    public User superuser() {
        return users.save(User.builder()
                .username(superuserAccount.username())
                .email(superuserAccount.email())
                .passwordHash("not-used")
                .role(Role.ADMIN)
                .build());
    }

    public Holding holding(User user, Player player, int quantity) {
        return holdings.save(Holding.issue(user, player, quantity, next()));
    }

    /** Venta LIMIT en el libro, con sus tokens reservados en la tenencia (que tiene que existir). */
    public MarketOrder sell(User user, Player player, int quantity, String limitPrice) {
        Holding holding = holdings.findByUserIdAndPlayerId(user.getId(), player.getId()).orElseThrow();
        Instant now = next();
        holding.reserve(quantity, now);
        holdings.save(holding);
        return orders.save(MarketOrder.limit(user, player, OrderSide.SELL, quantity, new BigDecimal(limitPrice), now));
    }

    /** Compra LIMIT en el libro; los créditos que reserva se calculan desde la orden. */
    public MarketOrder buy(User user, Player player, int quantity, String limitPrice) {
        return orders.save(MarketOrder.limit(user, player, OrderSide.BUY, quantity, new BigDecimal(limitPrice), next()));
    }

    /**
     * Marca como ejecutados {@code quantity} tokens de una orden sembrada. En una venta descuenta esos tokens de la
     * tenencia y de su reserva; no mueve créditos ni crea operaciones.
     */
    public MarketOrder fill(MarketOrder order, int quantity) {
        Instant now = next();
        if (order.getSide() == OrderSide.SELL) {
            Holding holding = holdings.findByUserIdAndPlayerId(order.getUser().getId(), order.getPlayer().getId())
                    .orElseThrow();
            holding.consumeReserved(quantity, now);
            holdings.save(holding);
        }
        order.fill(quantity, now);
        return orders.save(order);
    }

    private Instant next() {
        return BASE.plusSeconds(++tick);
    }
}
