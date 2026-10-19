package com.example.demo.repository;

import com.example.demo.model.PlayerStatsSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso append-only al historial de estadísticas. */
public interface PlayerStatsSnapshotRepository extends Repository<PlayerStatsSnapshot, Long> {

    PlayerStatsSnapshot save(PlayerStatsSnapshot snapshot);

    <S extends PlayerStatsSnapshot> List<S> saveAll(Iterable<S> snapshots);

    <S extends PlayerStatsSnapshot> List<S> saveAllAndFlush(Iterable<S> snapshots);

    Optional<PlayerStatsSnapshot> findById(Long id);

    @Query("""
            select s.player.id as playerId, s.sourceFetchedAt as sourceFetchedAt
            from PlayerStatsSnapshot s
            where s.player.id in :playerIds
              and s.sourceFetchedAt in :sourceFetchedAts
            """)
    List<ExistingSnapshotKey> findExistingKeys(@Param("playerIds") Collection<Long> playerIds,
                                               @Param("sourceFetchedAts") Collection<Instant> sourceFetchedAts);

    List<PlayerStatsSnapshot> findByPlayerIdOrderByCapturedAtDescIdDesc(Long playerId, Pageable pageable);

    long countByPlayerIdAndSourceFetchedAt(Long playerId, Instant sourceFetchedAt);

    interface ExistingSnapshotKey {
        Long getPlayerId();

        Instant getSourceFetchedAt();
    }
}
