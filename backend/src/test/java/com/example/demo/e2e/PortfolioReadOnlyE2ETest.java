package com.example.demo.e2e;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import com.example.demo.service.market.PortfolioService;
import com.example.demo.support.AuthFixtures;
import com.example.demo.support.E2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class PortfolioReadOnlyE2ETest {

    @Autowired UserRepository users;
    @Autowired PortfolioService portfolioService;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void allThreeGetsLeaveEveryFinancialTableUnchanged() {
        User user = AuthFixtures.user(users, "readonly-" + UUID.randomUUID(), Role.USER);
        user.setBalance(new BigDecimal("777.00"));
        users.save(user);
        Map<String, List<Map<String, Object>>> before = snapshot();
        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        portfolioService.portfolio(user.getId(), authentication);
        portfolioService.transactions(user.getId(), authentication, 0, 20);
        authService.me(user.getUsername());

        assertThat(snapshot()).isEqualTo(before);
        assertThat(users.findById(user.getId()).orElseThrow().getBalance()).isEqualByComparingTo("777.00");
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("users", "holdings", "orders", "trades", "audit_logs")) {
            result.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        }
        return result;
    }
}
