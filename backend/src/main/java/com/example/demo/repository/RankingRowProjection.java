package com.example.demo.repository;

import java.math.BigDecimal;

public interface RankingRowProjection {
    Long getRank();
    Long getPlayerId();
    String getName();
    String getTeam();
    String getLeague();
    BigDecimal getCurrentValue();
    BigDecimal getPreviousValue();
    String getStrategy();
    Integer getVersion();
}
