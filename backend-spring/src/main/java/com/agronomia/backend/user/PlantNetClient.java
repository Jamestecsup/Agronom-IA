package com.agronomia.backend.user;

import com.agronomia.backend.images.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Component
public class PlantNetClient {
    public record Photo(Path path, String mimeType) {}

    private final RestClient http;
    private final String base;
    private final String key;
    private final String project;
    private final String language;

    public PlantNetClient(@Value("${PLANTNET_BASE_URL:https://my-api.plantnet.org}") String base,
                          @Value("${PLANTNET_API_KEY:}") String key,
                          @Value("${PLANTNET_PROJECT:all}") String project,
                          @Value("${PLANTNET_LANG:es}") String language) {
        this.base = base.replaceFirst("/+$", "");
        this.key = key;
        this.project = project;
        this.language = language;
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(90)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public JsonNode identify(List<Photo> photos, String organ) {
        if (key.isBlank()) throw unavailable();
        var parts = new LinkedMultiValueMap<String, Object>();
        for (Photo photo : photos) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(photo.mimeType()));
            parts.add("images", new HttpEntity<>(new FileSystemResource(photo.path()), headers));
        }
        for (Photo ignored : photos) parts.add("organs", organ);
        var uri = UriComponentsBuilder.fromUriString(base).pathSegment("v2", "identify", project)
                .queryParam("api-key", key).queryParam("lang", language).queryParam("nb-results", 10)
                .build().encode().toUri();
        try {
            JsonNode response = http.post().uri(uri).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts).retrieve().body(JsonNode.class);
            if (response == null || !response.isObject()) throw unavailable();
            return response;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            throw unavailable();
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo consultar Pl@ntNet.");
    }
}
