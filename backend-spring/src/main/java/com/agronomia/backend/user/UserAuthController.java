package com.agronomia.backend.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/agronomia-api/api")
public class UserAuthController {
    private final UserAccounts accounts;
    private final JdbcTemplate jdbc;

    public UserAuthController(UserAccounts accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody UserAccounts.Credentials data) {
        return accounts.register(data);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody UserAccounts.Credentials data, HttpServletRequest request) {
        return accounts.login(data, request.getRemoteAddr());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean database;
        try {
            database = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (org.springframework.dao.DataAccessException error) {
            database = false;
        }
        return Map.of("success", database, "status", database ? "ok" : "degraded",
                "service", "agronomia-api", "database", database);
    }
}
