package com.agronomia.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login del panel y comprobación de sesión.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminDataService dataService;

    public AdminAuthController(AdminAuthService authService, AdminDataService dataService) {
        this.authService = authService;
        this.dataService = dataService;
    }

    @PostMapping("/auth/login")
    public AdminDtos.LoginResponse login(
            @RequestBody(required = false) AdminDtos.LoginRequest request
    ) {
        if (request == null) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "Envía usuario y contraseña.");
        }
        return authService.login(request.username(), request.password());
    }

    /** Devuelve la sesión del token enviado (lo valida el interceptor). */
    @GetMapping("/auth/me")
    public AdminDtos.MeResponse me(HttpServletRequest request) {
        Object username = request.getAttribute(AdminAuthInterceptor.ATTR_USERNAME);
        return new AdminDtos.MeResponse(username == null ? "" : username.toString(), "admin");
    }

    /** Health público: útil para saber si el panel y MySQL responden. */
    @GetMapping("/health")
    public AdminDtos.HealthResponse health() {
        return new AdminDtos.HealthResponse("ok", "admin", dataService.databaseUp());
    }
}
