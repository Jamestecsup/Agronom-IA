package com.agronomia.backend.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/agronomia-api/api")
public class UserPlantsController {
    private final UserAccounts accounts;
    private final UserPlants plants;

    public UserPlantsController(UserAccounts accounts, UserPlants plants) {
        this.accounts = accounts;
        this.plants = plants;
    }

    @PostMapping("/identify")
    public JsonNode identify(@RequestHeader(name = "Authorization", required = false) String bearer,
                             @RequestParam(name = "batch_id", defaultValue = "") String batch,
                             @RequestParam(defaultValue = "auto") String organ) {
        return plants.identify(accounts.requireUser(bearer), batch, organ);
    }

    @PostMapping("/confirm")
    public JsonNode confirm(@RequestHeader(name = "Authorization", required = false) String bearer,
                            @RequestParam(name = "batch_id", defaultValue = "") String batch,
                            @RequestParam(name = "scientific_name", defaultValue = "") String scientificName) {
        return plants.confirm(accounts.requireUser(bearer), batch, scientificName);
    }

    @GetMapping("/plants")
    public Map<String, Object> plants(@RequestHeader(name = "Authorization", required = false) String bearer) {
        return plants.list(accounts.requireUser(bearer));
    }

    @GetMapping("/plants/{plantId}/cover")
    public ResponseEntity<FileSystemResource> cover(@RequestHeader(name = "Authorization", required = false) String bearer,
                                                     @PathVariable String plantId) {
        return plants.cover(accounts.requireUser(bearer), plantId);
    }
}
