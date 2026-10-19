package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class ProviderHttp {
    static final ObjectMapper JSON = new ObjectMapper();
    // Open WebUI's local HTTP endpoint is commonly served by ASGI/HTTP/1.1; pin the wire protocol.
    static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();

    private ProviderHttp() { }

    static HttpResponse<String> get(String url, Duration timeout, String authorization) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Accept", "application/json").GET();
        if (authorization != null && !authorization.isBlank()) request.header("Authorization", authorization);
        try { return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString()); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "Se canceló la consulta al proveedor de IA.", interrupted);
        } catch (IOException | IllegalArgumentException error) {
            throw new ChatProviderException(HttpStatus.SERVICE_UNAVAILABLE, "El proveedor de IA no está disponible.", error);
        }
    }

    static JsonNode json(HttpResponse<String> response, String service) {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw status(response.statusCode(), service);
        try { return JSON.readTree(response.body()); }
        catch (IOException error) {
            throw new ChatProviderException(HttpStatus.BAD_GATEWAY, service + " devolvió una respuesta no válida.", error);
        }
    }

    static ChatProviderException status(int status, String service) {
        if (status == 401 || status == 403)
            return new ChatProviderException(HttpStatus.BAD_GATEWAY, service + " rechazó la autenticación configurada.");
        if (status == 429)
            return new ChatProviderException(HttpStatus.TOO_MANY_REQUESTS, service + " alcanzó su límite de uso. Inténtalo más tarde.");
        if (status == 408 || status == 504)
            return new ChatProviderException(HttpStatus.GATEWAY_TIMEOUT, service + " tardó demasiado en responder.");
        if (status >= 500)
            return new ChatProviderException(HttpStatus.BAD_GATEWAY, service + " presenta un problema temporal.");
        return new ChatProviderException(HttpStatus.BAD_GATEWAY, service + " no aceptó la solicitud (HTTP " + status + ").");
    }
}
