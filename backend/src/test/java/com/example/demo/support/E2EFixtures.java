package com.example.demo.support;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import com.example.demo.repository.PlayerRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Canonical database fixtures shared by resilience e2e tests and by features
 * 017, 018 and 021. The helper owns all repository access; consumers only use
 * this component and the in-memory {@link FixtureSet} it returns.
 *
 * <p>Call {@link #createPlayersFromFiveLeagues()} once after the common e2e
 * cleanup. Generated database IDs and persistence order are intentionally not
 * stable, while external IDs and the league/team/position combinations are.
 * Persistence failures are allowed to propagate so a test never observes a
 * partial fixture set.</p>
 */
@Component
public class E2EFixtures {

    private final PlayerRepository playerRepository;

    E2EFixtures(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public FixtureSet createPlayersFromFiveLeagues() {
        List<Player> players = List.of(
                player("91001", "E2E Premier Forward", "Arsenal FC", League.PREMIER_LEAGUE,
                        "England", 24, "10.00", Position.FORWARD),
                player("91002", "E2E Liga Utility", "Real Madrid", League.LA_LIGA,
                        "Spain", 27, "11.00", Position.DEFENDER, Position.MIDFIELDER),
                player("91003", "E2E Serie Goalkeeper", "Inter", League.SERIE_A,
                        "Italy", 29, "12.00", Position.GOALKEEPER),
                player("91004", "E2E Bundesliga Midfielder", "Bayern München", League.BUNDESLIGA,
                        "Germany", 25, "13.00", Position.MIDFIELDER),
                player("91005", "E2E Ligue Defender", "Paris Saint-Germain", League.LIGUE_1,
                        "France", 26, "14.00", Position.DEFENDER));

        return new FixtureSet(playerRepository.saveAllAndFlush(players));
    }

    private static Player player(String externalId, String fullName, String team, League league,
                                 String nationality, int age, String marketValue,
                                 Position firstPosition, Position... remainingPositions) {
        EnumSet<Position> positions = EnumSet.of(firstPosition, remainingPositions);
        return Player.builder()
                .externalId(externalId)
                .fullName(fullName)
                .team(team)
                .league(league)
                .positions(positions)
                .nationality(nationality)
                .age(age)
                .marketValue(new BigDecimal(marketValue))
                .build();
    }

    public record FixtureSet(List<Player> all) {

        public FixtureSet {
            all = List.copyOf(all);
        }

        public Player forLeague(League league) {
            Objects.requireNonNull(league, "league must not be null");
            return uniqueMatch("league " + league, player -> player.getLeague() == league);
        }

        public Player forTeam(String team) {
            Objects.requireNonNull(team, "team must not be null");
            return uniqueMatch("team " + team, player -> player.getTeam().equals(team));
        }

        public List<Player> withPosition(Position position) {
            Objects.requireNonNull(position, "position must not be null");
            List<Player> matches = all.stream()
                    .filter(player -> player.getPositions().contains(position))
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalStateException("No fixture found for position " + position);
            }
            return matches;
        }

        private Player uniqueMatch(String criterion, Predicate<Player> predicate) {
            List<Player> matches = all.stream().filter(predicate).toList();
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "Expected exactly one fixture for " + criterion + " but found " + matches.size());
            }
            return matches.get(0);
        }
    }
}
