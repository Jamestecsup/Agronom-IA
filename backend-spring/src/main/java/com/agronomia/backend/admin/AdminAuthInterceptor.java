package com.agronomia.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Exige `Authorization: Bearer <token>` en todo /api/admin/** salvo el login
 * y el health check. Deja la sesión en atributos de la petición.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "adminUserId";
    public static final String ATTR_USERNAME = "adminUsername";

    private final AdminTokenService tokens;

    public AdminAuthInterceptor(AdminTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        // El preflight de CORS no lleva token: se deja pasar.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response);
        }
        Optional<AdminTokenService.AdminSession> session =
                tokens.verify(header.substring("Bearer ".length()).trim());
        if (session.isEmpty()) {
            return reject(response);
        }

        request.setAttribute(ATTR_USER_ID, session.get().userId());
        request.setAttribute(ATTR_USERNAME, session.get().username());
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"error\":\"Sesión no válida o caducada. Vuelve a entrar.\"}"
        );
        return false;
    }
}
