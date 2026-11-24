package com.agronomia.backend.user;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.security.DjangoPasswordEncoder;
import com.agronomia.backend.security.LoginAttempts;
import com.agronomia.backend.security.UserTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAccountsTest {
    private static final String FIXTURE = "fixture-django-codex";
    // Fixture producido por PBKDF2PasswordHasher de Django, ajeno a cualquier cuenta.
    private static final String DJANGO_HASH =
            "pbkdf2_sha256$600000$salFixtureDjango123456$skEcrEpIAU0qj0TXypHX+P0Ul0oo9wKC95FJuXRBeUA=";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DjangoPasswordEncoder encoder = new DjangoPasswordEncoder();
    private final UserTokens tokens = new UserTokens("f".repeat(32));
    private final UserAccounts accounts = new UserAccounts(jdbc, encoder, tokens, new LoginAttempts());

    @Test
    void validatesDjangoHashAndGeneratesCompatiblePassword() {
        assertThat(encoder.matches(FIXTURE, DJANGO_HASH)).isTrue();
        assertThat(encoder.matches("incorrecto", DJANGO_HASH)).isFalse();
        String hash = encoder.encode(FIXTURE);
        assertThat(hash).startsWith("pbkdf2_sha256$600000$").isNotEqualTo(DJANGO_HASH);
        assertThat(encoder.matches(FIXTURE, hash)).isTrue();
    }

    @Test
    void registersWithUtcAndLimitsRegistrationsByAddress() {
        when(jdbc.queryForObject("SELECT id FROM auth_user WHERE username = ?", Long.class, "prueba"))
                .thenReturn(4L);
        assertThat(accounts.register(new UserAccounts.Credentials("prueba", FIXTURE, null), "local-fixture"))
                .containsEntry("role", "user");
        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("UTC_TIMESTAMP(6)"), eq("prueba"), encoded.capture(), eq(""));
        assertThat(encoder.matches(FIXTURE, encoded.getValue())).isTrue();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> accounts.register(new UserAccounts.Credentials("prueba", "corta", null), "local-fixture"))
                    .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThatThrownBy(() -> accounts.register(new UserAccounts.Credentials("prueba", FIXTURE, null), "local-fixture"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void logsInWithDjangoPasswordAndChecksAccountStillActive() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(4L);
        when(row.getString("username")).thenReturn("prueba");
        when(row.getString("password")).thenReturn(DJANGO_HASH);
        when(row.getBoolean("is_active")).thenReturn(true);
        when(row.getString("role")).thenReturn("user");
        when(jdbc.query(contains("LEFT JOIN perfiles_usuario"), any(RowMapper.class), eq("prueba")))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(row, 0)));
        var login = accounts.login(new UserAccounts.Credentials("prueba", FIXTURE, null), "otro-fixture");
        String bearer = "Bearer " + login.get("token");
        when(jdbc.queryForObject(contains("is_active=TRUE"), eq(Integer.class), eq(4L))).thenReturn(1, 0);
        assertThat(accounts.requireUser(bearer)).isEqualTo(4L);
        assertThatThrownBy(() -> accounts.requireUser(bearer))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> tokens.requireUserId(bearer + ".alterado"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> accounts.requireUser(null)).isInstanceOf(ApiException.class);
    }
}
