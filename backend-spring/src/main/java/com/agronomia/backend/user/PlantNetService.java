package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class PlantNetService implements PlantIdentificationClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;

    public PlantNetService(ObjectMapper json,
                           @Value("${plantnet.api-key:${PLANTNET_API_KEY:}}") String apiKey,
                           @Value("${plantnet.base-url:${PLANTNET_BASE_URL:https://my-api.plantnet.org/}}") String baseUrl) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> identify(List<byte[]> images, String organ, String project, String lang) {
        if (apiKey.isBlank()) throw new UserApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PLANTNET_API_KEY no configurada.");
        try {
            String boundary = "----AgronomIA" + UUID.randomUUID();
            byte[] body = multipart(images, organ == null || organ.isBlank() ? "auto" : organ, boundary);
            String url = baseUrl.replaceAll("/+$", "") + "/v2/identify/" + (project == null ? "all" : project)
                    + "?api-key=" + enc(apiKey) + "&lang=" + enc(lang == null ? "es" : lang) + "&nb-results=10";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return Map.of("results", List.of());
            if (resp.statusCode() != 200) throw new UserApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Pl@ntNet HTTP " + resp.statusCode());
            return json.readValue(resp.body(), Map.class);
        } catch (UserApiException e) { throw e; }
        catch (Exception e) { throw new UserApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "No se pudo consultar Pl@ntNet."); }
    }

    private byte[] multipart(List<byte[]> images, String organ, String boundary) throws IOException {
        var baos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < images.size(); i++) {
            byte[] data = images.get(i);
            String[] type = detectType(data);
            write(baos, "--" + boundary + "\r\n");
            write(baos, "Content-Disposition: form-data; name=\"images\"; filename=\"plant" + i + "." + type[0] + "\"\r\n");
            write(baos, "Content-Type: " + type[1] + "\r\n\r\n");
            baos.write(data);
            write(baos, "\r\n");
            write(baos, "--" + boundary + "\r\n");
            write(baos, "Content-Disposition: form-data; name=\"organs\"\r\n\r\n");
            write(baos, organ + "\r\n");
        }
        write(baos, "--" + boundary + "--\r\n");
        return baos.toByteArray();
    }

    private String[] detectType(byte[] data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new UserApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "La foto no tiene un formato válido.");
        } catch (UserApiException e) { throw e; } catch (Exception ignored) {}
        if (data.length >= 3 && (data[0]&0xFF)==0xFF && (data[1]&0xFF)==0xD8) return new String[]{"jpg","image/jpeg"};
        if (data.length >= 8 && (data[0]&0xFF)==0x89 && data[1]==0x50 && data[2]==0x4E && data[3]==0x47) return new String[]{"png","image/png"};
        throw new UserApiException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "La foto debe estar en formato JPG o PNG.");
    }

    private void write(java.io.OutputStream out, String s) throws IOException { out.write(s.getBytes(StandardCharsets.UTF_8)); }
    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
