package com.agronomia.backend.user;

import com.agronomia.backend.images.ImageDtos;
import com.agronomia.backend.images.ImageBatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/agronomia-api/api")
public class UserController {
    private final UserAuthService auth;
    private final UserTokenService tokens;
    private final ImageBatchService images;
    private final UserPlantService plants;
    private final IdentificationPipelineService pipeline;
    private final UserChatService chat;
    private final UserScheduleService schedule;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final Path uploadDir;

    public UserController(UserAuthService auth, UserTokenService tokens, ImageBatchService images,
                          UserPlantService plants, IdentificationPipelineService pipeline, UserChatService chat,
                          UserScheduleService schedule,
                          org.springframework.jdbc.core.JdbcTemplate jdbc,
                          @Value("${plant.storage.directory:${PLANT_UPLOAD_DIR:C:/xampp/plant-images}}") String uploadDir) {
        this.auth = auth; this.tokens = tokens; this.images = images; this.plants = plants; this.pipeline = pipeline; this.chat = chat;
        this.schedule = schedule; this.jdbc = jdbc; this.uploadDir = Path.of(uploadDir);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) UserDtos.AuthRequest body) {
        if (body == null) throw new UserApiException(HttpStatus.BAD_REQUEST, "JSON inválido.");
        var r = auth.register(body.username(), body.password(), body.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) UserDtos.AuthRequest body) {
        if (body == null) throw new UserApiException(HttpStatus.BAD_REQUEST, "JSON inválido.");
        return ResponseEntity.ok(auth.login(body.username(), body.password()));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("success", true, "status", "ok", "service", "agronomia-api", "database", true);
    }

    @PostMapping("/images.php")
    public ResponseEntity<ImageDtos.UploadResponse> upload(
            @RequestParam(name = "photos[]", required = false) List<MultipartFile> photos,
            HttpServletRequest request) {
        var session = requireUser(request);
        var resp = images.upload(photos, session.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/identify")
    public Map<String, Object> identify(@RequestParam String batch_id,
                                        @RequestParam(defaultValue = "auto") String organ,
                                        HttpServletRequest request) {
        var s = requireUser(request);
        List<Map<String,Object>> rows = jdbc.query("SELECT relative_path FROM imagenes_planta WHERE batch_id=? AND user_id=? ORDER BY created_at ASC",
                (rs,i) -> Map.of("p", rs.getString("relative_path")), batch_id, s.userId());
        if (rows.isEmpty()) throw new UserApiException(HttpStatus.NOT_FOUND, "No se encontraron imágenes para ese batch_id.");
        List<byte[]> blobs = new ArrayList<>();
        for (Map<String,Object> r : rows) {
            try { blobs.add(Files.readAllBytes(uploadDir.resolve(String.valueOf(r.get("p"))))); }
            catch (Exception e) { throw new UserApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer una imagen almacenada."); }
        }
        Map<String,Object> result = pipeline.identify(blobs, organ);
        plants.saveRun(s.userId(), batch_id, organ, String.valueOf(result.get("status")), result.get("raw"));
        if ("failed".equals(result.get("status"))) throw new UserApiException(HttpStatus.BAD_GATEWAY, String.valueOf(result.get("message")));
        Map<String,Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("status", result.get("status"));
        if ("identified".equals(result.get("status"))) {
            Map<String,Object> plant = plants.asMap(result.get("plant"));
            plants.saveIdentification(s.userId(), batch_id, plant);
            resp.put("plant", plant);
        } else if ("ambiguous".equals(result.get("status"))) {
            resp.put("candidates", result.get("candidates"));
        } else {
            resp.put("message", result.get("message"));
            if ("insufficient".equals(result.get("status"))) resp.put("plant_name", result.get("plant_name"));
        }
        if (result.containsKey("candidates") && !resp.containsKey("candidates")) resp.put("candidates", result.get("candidates"));
        return resp;
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestParam String batch_id,
                                       @RequestParam String scientific_name,
                                       HttpServletRequest request) {
        var s = requireUser(request);
        Map<String, Object> raw = plants.loadLatestRunRaw(s.userId(), batch_id);
        List<Map<String, Object>> candidates = plants.loadLatestRunCandidates(s.userId(), batch_id);
        if (candidates.isEmpty()) throw new UserApiException(HttpStatus.NOT_FOUND, "No se encontr? una identificaci?n previa para ese lote.");

        Map<String, Object> chosenResult = null;
        Object results = raw.get("results");
        if (results instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Map<String, Object> sp = plants.asMap(m.get("species"));
                String name = plants.firstNonBlank(plants.str(sp.get("scientificNameWithoutAuthor")), plants.str(sp.get("scientificName")));
                if (scientific_name.equals(name)) { chosenResult = plants.asMap(m); break; }
            }
        }
        Map<String, Object> chosenCandidate = candidates.stream()
                .filter(c -> scientific_name.equals(c.get("scientific_name")))
                .findFirst().orElse(null);
        if (chosenResult == null && chosenCandidate == null) {
            throw new UserApiException(HttpStatus.BAD_REQUEST, "La especie elegida no coincide con las candidatas.");
        }
        if (chosenResult == null) {
            chosenResult = new LinkedHashMap<>();
            Map<String, Object> species = new LinkedHashMap<>();
            species.put("scientificNameWithoutAuthor", chosenCandidate.get("scientific_name"));
            species.put("scientificName", chosenCandidate.get("scientific_name"));
            species.put("commonNames", chosenCandidate.getOrDefault("common_names", List.of()));
            species.put("family", Map.of("scientificNameWithoutAuthor", chosenCandidate.getOrDefault("family", "")));
            chosenResult.put("species", species);
            chosenResult.put("score", chosenCandidate.getOrDefault("confidence", 0));
        }
        Map<String, Object> plant = pipeline.generatePlantInfo(chosenResult, candidates);
        plants.saveIdentification(s.userId(), batch_id, plant);
        return Map.of("success", true, "status", "identified", "plant", plant);
    }

    @GetMapping("/plants")
    public Map<String, Object> plants(HttpServletRequest request) {
        var s = requireUser(request);
        return Map.of("success", true, "plants", plants.listPlants(s.userId()));
    }

    @GetMapping("/plants/{plantId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable String plantId, HttpServletRequest request) {
        var s = requireUser(request);
        String path = plants.findCoverPath(s.userId(), plantId).orElseThrow(() -> new UserApiException(HttpStatus.NOT_FOUND, "No hay foto de portada."));
        try {
            byte[] bytes = Files.readAllBytes(uploadDir.resolve(path));
            String mime = path.toLowerCase().endsWith(".png") ? "image/png" : path.toLowerCase().endsWith(".webp") ? "image/webp" : "image/jpeg";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(bytes);
        } catch (Exception e) { throw new UserApiException(HttpStatus.NOT_FOUND, "No se pudo leer la foto de portada."); }
    }

    @GetMapping("/chat/history")
    public Map<String, Object> chatHistory(@RequestParam String plant_id, HttpServletRequest request) {
        var s = requireUser(request);
        return chat.history(s.userId(), plant_id);
    }

    @GetMapping("/chat/images")
    public Map<String, Object> chatImages(@RequestParam String plant_id, HttpServletRequest request) {
        var s = requireUser(request);
        return Map.of("success", true, "images", chat.progressImages(s.userId(), plant_id));
    }

    @GetMapping("/chat/images/{imageId}")
    public ResponseEntity<byte[]> chatImage(@PathVariable String imageId, HttpServletRequest request) {
        var s = requireUser(request);
        Map<String, Object> row = chat.progressImage(s.userId(), imageId);
        try {
            byte[] bytes = Files.readAllBytes(uploadDir.resolve(String.valueOf(row.get("relative_path"))));
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(String.valueOf(row.get("mime_type")))).body(bytes);
        } catch (Exception e) {
            throw new UserApiException(HttpStatus.NOT_FOUND, "No se pudo leer la imagen de progreso.");
        }
    }

    @PostMapping(value = "/chat", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public Map<String, Object> chat(@RequestParam(required = false) String plant_id,
                                    @RequestParam(required = false) String message,
                                    @RequestParam(required = false) MultipartFile image,
                                    HttpServletRequest request) {
        var s = requireUser(request);
        byte[] img = null; String mime = "image/jpeg";
        if (image != null && !image.isEmpty()) {
            try { img = image.getBytes(); mime = image.getContentType() == null ? "image/jpeg" : image.getContentType(); }
            catch (Exception e) { throw new UserApiException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen del chat."); }
        }
        return chat.reply(s.userId(), plant_id, message, img, mime, message);
    }

    @GetMapping("/schedule")
    public Map<String, Object> weeklySchedule(HttpServletRequest request) {
        return schedule.weekly(requireUser(request).userId());
    }

    @PostMapping(value = "/schedule/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importSchedule(@RequestParam(required = false) MultipartFile pdf, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schedule.importPdf(requireUser(request).userId(), pdf));
    }

    @GetMapping("/schedule/import/{importId}")
    public Map<String, Object> pendingScheduleImport(@PathVariable String importId, HttpServletRequest request) {
        return schedule.pendingImport(requireUser(request).userId(), importId);
    }

    @PostMapping("/schedule/import/{importId}")
    public Map<String, Object> confirmScheduleImport(@PathVariable String importId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return schedule.confirmImport(requireUser(request).userId(), importId, body == null ? List.of() : body.get("classes"));
    }

    @DeleteMapping("/schedule/import/{importId}")
    public Map<String, Object> cancelScheduleImport(@PathVariable String importId, HttpServletRequest request) {
        return schedule.cancelImport(requireUser(request).userId(), importId);
    }

    @PostMapping("/schedule/care-plan/draft")
    public Map<String, Object> carePlanDraft(HttpServletRequest request) {
        return schedule.draftCarePlan(requireUser(request).userId());
    }

    @PostMapping("/schedule/care-plan/confirm")
    public Map<String, Object> carePlanConfirm(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        return schedule.confirmCarePlan(requireUser(request).userId(), body == null ? List.of() : body.get("tasks"));
    }

    @PatchMapping("/schedule/care-plan/tasks/{taskId}")
    public Map<String, Object> rescheduleCareTask(@PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        if (body == null) throw new UserApiException(HttpStatus.BAD_REQUEST, "Faltan datos de reprogramacion.");
        int day = ((Number) body.getOrDefault("day_of_week", 0)).intValue();
        String start = String.valueOf(body.getOrDefault("start_time", ""));
        return schedule.rescheduleTask(requireUser(request).userId(), taskId, day, start);
    }

    @DeleteMapping("/schedule/care-plan/tasks/{taskId}")
    public Map<String, Object> deleteCareTask(@PathVariable String taskId, HttpServletRequest request) {
        return schedule.deleteTask(requireUser(request).userId(), taskId);
    }

    private UserTokenService.UserSession requireUser(HttpServletRequest request) {
        String authz = request.getHeader("Authorization");
        String token = (authz != null && authz.startsWith("Bearer ")) ? authz.substring(7).trim() : "";
        return tokens.verify(token).orElseThrow(() -> new UserApiException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
