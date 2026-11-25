package com.agronomia.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAuthInterceptorTest {
    private final AdminTokenService tokens = mock(AdminTokenService.class);
    private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(tokens);

    @Test
    void optionsRequestIsAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void rejectsMissingBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsValidBearerToken() throws Exception {
        when(tokens.verify("good-token")).thenReturn(Optional.of(new AdminTokenService.AdminSession(1L, "admin")));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute(AdminAuthInterceptor.ATTR_USER_ID)).isEqualTo(1L);
        assertThat(request.getAttribute(AdminAuthInterceptor.ATTR_USERNAME)).isEqualTo("admin");
    }
}
