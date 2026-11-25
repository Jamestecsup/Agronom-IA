package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTokenServiceTest {

    private UserTokenService service(JdbcTemplate jdbc) {
        return new UserTokenService(
                "secret-test",
                8L,
                "",
                "agronomia.user-api",
                1209600L,
                jdbc,
                new ObjectMapper()
        );
    }

    @Test
    void issueAndVerifyRoundTrip() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserTokenService service = service(jdbc);

        String token = service.issue(1L, "tester");
        UserTokenService.UserSession session = service.verify(token).orElseThrow();

        assertThat(session.userId()).isEqualTo(1L);
        assertThat(session.username()).isEqualTo("tester");
    }

    @Test
    void verifyRejectsInvalidToken() {
        UserTokenService service = service(mock(JdbcTemplate.class));
        assertThat(service.verify("not-a-token")).isEmpty();
    }

    @Test
    void verifyRejectsMalformedLegacyTokenWithoutDjangoSecret() {
        UserTokenService service = service(mock(JdbcTemplate.class));
        assertThat(service.verify("legacy.payload.signature")).isEmpty();
    }
}
