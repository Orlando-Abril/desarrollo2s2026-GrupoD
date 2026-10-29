package com.example.demo.repository.market;

import com.example.demo.model.User;
import com.example.demo.service.market.MarketPrincipal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Acceso al saldo de los usuarios para el mercado. Existe para no modificar {@code UserRepository},
 * archivo protegido de Entrega 1.
 */
public interface MarketUserRepository extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findLockedById(@Param("id") Long id);

    /** Id y username sin cargar la entidad: el motor de calce carga el {@code User} sólo con su lock (spec 027). */
    @Query("select new com.example.demo.service.market.MarketPrincipal(u.id, u.username) from User u"
            + " where u.username = :username")
    Optional<MarketPrincipal> findPrincipalByUsername(@Param("username") String username);

    @Query("select u.balance from User u where u.id = :id")
    Optional<BigDecimal> findBalanceById(@Param("id") Long id);

    /**
     * Suma {@code delta} al saldo y devuelve las filas afectadas (0 si el usuario no existe). Sólo la usa el
     * motor de calce, con el usuario bloqueado por {@link #findLockedById}, y cada llamada se audita como
     * {@code BALANCE_CHANGED} en la misma transacción (spec 026, FR-028). No valida saldo negativo.
     * {@code users.balance} tiene escala 4 (spec 027). El motor de calce no usa este método: modifica el saldo
     * sobre la entidad bloqueada por {@link #findLockedById}, porque {@code clearAutomatically} vacía el
     * contexto de persistencia y dejaría desconectadas las órdenes y tenencias del calce.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.balance = u.balance + :delta where u.id = :id")
    int addToBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
