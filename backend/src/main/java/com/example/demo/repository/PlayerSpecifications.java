package com.example.demo.repository;

import com.example.demo.model.League;
import com.example.demo.model.Player;
import com.example.demo.model.Position;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class PlayerSpecifications {
    private PlayerSpecifications() {
    }

    public static Specification<Player> withFilters(League league, String team, Position position) {
        return Specification.where(hasLeague(league)).and(hasTeam(team)).and(hasPosition(position));
    }

    static Specification<Player> hasLeague(League league) {
        return (root, query, cb) -> league == null ? cb.conjunction() : cb.equal(root.get("league"), league);
    }

    static Specification<Player> hasTeam(String team) {
        return (root, query, cb) -> team == null ? cb.conjunction()
                : cb.equal(cb.lower(root.get("team")), team.trim().toLowerCase());
    }

    static Specification<Player> hasPosition(Position position) {
        return (root, query, cb) -> {
            if (position == null) return cb.conjunction();
            query.distinct(true);
            return cb.equal(root.join("positions", JoinType.INNER), position);
        };
    }
}
