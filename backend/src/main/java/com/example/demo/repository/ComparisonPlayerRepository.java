package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Lecturas de jugadores del comparador de estrategias (spec 031) con una cantidad constante de consultas: trae las
 * posiciones en la misma sentencia. Existe para no modificar {@code PlayerRepository}, archivo protegido de
 * Entrega 1, y no ofrece escrituras.
 */
public interface ComparisonPlayerRepository extends Repository<Player, Long> {

    @Query("""
            select distinct p from Player p left join fetch p.positions
            where (:league is null or p.league = :league)
              and (:position is null or exists (select 1 from Player f join f.positions fp
                                                where f = p and fp = :position))
            """)
    List<Player> findForComparison(@Param("league") League league, @Param("position") Position position);

    @Query("select p.id from Player p where p.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
