package com.agronomia.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Datos del panel: estadísticas, usuarios, identificaciones y chat.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminDataController {

    private final AdminDataService dataService;

    public AdminDataController(AdminDataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/stats")
    public AdminDtos.StatsResponse stats() {
        return dataService.stats();
    }

    @GetMapping("/users")
    public List<AdminDtos.UserRow> users(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return dataService.users(q, limit);
    }

    @PatchMapping("/users/{id}")
    public AdminDtos.UserRow updateUser(
            @PathVariable long id,
            @RequestBody(required = false) AdminDtos.UpdateUserRequest body,
            HttpServletRequest request
    ) {
        if (body == null || (body.role() == null && body.active() == null)) {
            throw new AdminException(HttpStatus.BAD_REQUEST,
                    "Envía role y/o active para actualizar la cuenta.");
        }
        Object actor = request.getAttribute(AdminAuthInterceptor.ATTR_USER_ID);
        long actorId = actor instanceof Long value ? value : -1L;
        return dataService.updateUser(actorId, id, body.role(), body.active());
    }

    @GetMapping("/plants")
    public List<AdminDtos.PlantRow> plants(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return dataService.plants(limit);
    }

    @GetMapping("/chats")
    public List<AdminDtos.ChatRow> chats(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return dataService.chats(limit);
    }
}
