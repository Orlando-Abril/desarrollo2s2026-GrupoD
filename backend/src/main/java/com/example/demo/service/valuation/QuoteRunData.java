package com.example.demo.service.valuation;

import com.example.demo.repository.PlayerQuoteRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.repository.PlayerStatsRepository;
import org.springframework.stereotype.Component;

/** Datos persistidos que lee y escribe una corrida de recálculo: snapshot, jugadores, estadísticas y cotizaciones. */
@Component
public record QuoteRunData(StatsSnapshotService snapshots,
                           PlayerRepository players,
                           PlayerStatsRepository stats,
                           PlayerQuoteRepository quotes) {
}
