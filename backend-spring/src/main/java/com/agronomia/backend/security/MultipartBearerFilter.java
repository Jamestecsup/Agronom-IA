package com.agronomia.backend.security;

import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.images.ImageDtos;
import com.agronomia.backend.user.UserAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MultipartBearerFilter extends OncePerRequestFilter {
    private static final String API = "/agronomia-api/api/";
    private final UserAccounts accounts;
    private final ObjectMapper json;

    public MultipartBearerFilter(UserAccounts accounts, ObjectMapper json) {
        this.accounts = accounts;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String contentType = request.getContentType();
        return !path.startsWith(API) || contentType == null
                || !contentType.regionMatches(true, 0, "multipart/", 0, "multipart/".length())
                || path.equals(API + "register") || path.equals(API + "login") || path.equals(API + "health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // DispatcherServlet resuelve las partes después del filtro. Aquí solo se leen cabeceras.
            accounts.requireUser(request.getHeader(HttpHeaders.AUTHORIZATION));
        } catch (ApiException error) {
            response.setStatus(error.status().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getOutputStream(), new ImageDtos.ErrorResponse(false, error.getMessage()));
            return;
        }
        chain.doFilter(request, response);
    }
}
