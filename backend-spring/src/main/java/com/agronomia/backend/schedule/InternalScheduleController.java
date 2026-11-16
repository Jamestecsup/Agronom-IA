package com.agronomia.backend.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;

/** Endpoints de servicio a servicio: Django autentica a la cuenta móvil. */
@RestController
@RequestMapping("/api/internal/schedule")
public class InternalScheduleController {

    private final ScheduleAiService schedules;
    private final byte[] expectedToken;

    public InternalScheduleController(
            ScheduleAiService schedules,
            @Value("${schedule.internal.token}") String expectedToken
    ) {
        this.schedules = schedules;
        this.expectedToken = expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JsonNode parsePdf(
            @RequestHeader(value = "X-Schedule-Service-Key", required = false) String token,
            @RequestPart("pdf") MultipartFile pdf
    ) {
        requireService(token);
        return schedules.parseWeeklySchedule(pdf);
    }

    @PostMapping(value = "/care-plan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode draftCarePlan(
            @RequestHeader(value = "X-Schedule-Service-Key", required = false) String token,
            @RequestBody JsonNode request
    ) {
        requireService(token);
        return schedules.draftCarePlan(request);
    }

    private void requireService(String token) {
        byte[] supplied = (token == null ? "" : token).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Servicio no autorizado.");
        }
    }
}
