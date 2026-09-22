package com.example.demo.adapter.footballdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballDataResponse(List<Team> teams) {
    public FootballDataResponse {
        teams = teams == null ? List.of() : List.copyOf(teams);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(String name, List<SquadMember> squad) {
        public Team {
            squad = squad == null ? List.of() : List.copyOf(squad);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SquadMember(Long id, String name, String position,
                              LocalDate dateOfBirth, String nationality) {
    }
}
