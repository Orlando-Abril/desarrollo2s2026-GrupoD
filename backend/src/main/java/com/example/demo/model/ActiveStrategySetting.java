package com.example.demo.model;

import com.example.demo.model.market.AuditActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Configuración global de estrategias (spec 031): una única fila con id {@link #SINGLETON_ID}. Se crea con el
 * primer cambio de estrategia activa o con el primer recálculo que fija precios.
 * <p>
 * {@code activeCode} nulo significa "usar {@code valuation.active-strategy}". {@code marketStrategyCode} es la
 * estrategia activa del último recálculo que actualizó {@code Player.marketValue}.
 */
@Entity
@Table(name = "active_strategy_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveStrategySetting {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "active_code", length = 32)
    private String activeCode;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_actor_type", length = 16)
    private AuditActorType changedByActorType;

    @Column(name = "market_strategy_code", length = 32)
    private String marketStrategyCode;

    @Column(name = "market_strategy_applied_at")
    private Instant marketStrategyAppliedAt;

    public static ActiveStrategySetting empty() {
        ActiveStrategySetting setting = new ActiveStrategySetting();
        setting.id = SINGLETON_ID;
        return setting;
    }

    public void changeActive(String code, AuditActorType actorType, Long actorUserId, Instant at) {
        this.activeCode = Objects.requireNonNull(code, "code");
        this.changedByActorType = Objects.requireNonNull(actorType, "actorType");
        this.changedByUserId = actorUserId;
        this.changedAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Fija la estrategia de mercado supuesta cuando todavía no se registró ninguna: si nunca hubo un recálculo que
     * fijara precios, el {@code marketValue} vigente corresponde a la activa anterior al primer cambio.
     */
    public void assumeMarketIfUnknown(String code) {
        if (marketStrategyCode == null) {
            this.marketStrategyCode = Objects.requireNonNull(code, "code");
        }
    }

    public void recordMarket(String code, Instant at) {
        this.marketStrategyCode = Objects.requireNonNull(code, "code");
        this.marketStrategyAppliedAt = Objects.requireNonNull(at, "at");
    }
}
