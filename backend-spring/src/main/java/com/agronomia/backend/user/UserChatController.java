package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.agronomia.backend.images.ApiException;
import com.agronomia.backend.images.ImageMetadata;
import com.agronomia.backend.images.ImageValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agronomia-api/api")
public class UserChatController {
    private static final String PHOTO_PROMPT = "Comparto una foto del progreso de mi planta. ¿Qué observas y qué cuidado me recomiendas?";
    private final UserAccounts accounts;
    private final UserPlants plants;
    private final PlantCards cards;
    private final UserFiles files;
    private final ImageValidator validator;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiClient ai;
    private final TransactionTemplate transaction;

    public UserChatController(UserAccounts accounts, UserPlants plants, PlantCards cards, UserFiles files,
                              ImageValidator validator, JdbcTemplate jdbc, ObjectMapper json,
                              AiClient ai, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.plants = plants;
        this.cards = cards;
        this.files = files;
        this.validator = validator;
        this.jdbc = jdbc;
        this.json = json;
        this.ai = ai;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestHeader(name = "Authorization", required = false) String token,
                                    @RequestParam(defaultValue = "") String message,
                                    @RequestParam(name = "session_id", defaultValue = "default") String sessionId,
                                    @RequestParam(defaultValue = "") String context,
                                    @RequestParam(name = "plant_id", defaultValue = "") String plantId,
                                    @RequestParam(required = false) MultipartFile image) {
        long user = accounts.requireUser(token);
        var client = ai.forUser(user);
        if (image != null && !client.supportsVision()) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "El modelo activo no admite fotos.");
        }
        if (message.codePointCount(0, message.length()) > 4000) {
            throw error(HttpStatus.BAD_REQUEST, "El mensaje no puede superar 4000 caracteres.");
        }
        String caption = message.strip();
        if (caption.isEmpty() && image == null) {
            throw error(HttpStatus.BAD_REQUEST, "Escribe un mensaje o adjunta una foto.");
        }
        if (image != null && plantId.isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "Asocia la foto a una planta para guardarla en su chat.");
        }
        UserPlants.StoredPlant plant = plantId.isBlank() ? null : plants.requirePlant(user, plantId.strip());
        String session = plant != null ? plant.sessionId() : sessionId.strip();
        if (session.length() > 64) throw error(HttpStatus.BAD_REQUEST, "La sesión de chat no es válida.");
        String question = caption.isEmpty() ? PHOTO_PROMPT : caption;
        ImageMetadata photo = image == null ? null : validator.validate(image);
        byte[] photoBytes = photo == null ? null : readImage(image);
        var prompt = new ArrayList<Map<String, Object>>();
        prompt.add(AiClient.message("system", cards.skill("chat-assistant")));
        var previous = jdbc.query("""
                SELECT role,content FROM mensajes_chat WHERE user_id=? AND session_id=?
                ORDER BY created_at DESC,id DESC LIMIT 12
                """, (row, index) -> AiClient.message(row.getString(1), row.getString(2)), user, session);
        Collections.reverse(previous);
        prompt.addAll(previous);
        String subject = context.strip();
        if (plant != null) subject = context(plant);
        else if (subject.isBlank()) subject = plants.plants(user).stream().findFirst()
                .map(UserChatController::context).orElse("Aún no se ha detectado ninguna planta. Sugiere usar el escáner.");
        var turn = new ArrayList<Map<String, Object>>();
        if (client.inputBudget() != Integer.MAX_VALUE && subject.length() > client.inputBudget() / 2) {
            subject = subject.substring(0, client.inputBudget() / 2);
        }
        turn.add(Map.of("type", "text", "text", question + "\n\n" + subject));
        if (photo != null) {
            turn.add(Map.of("type", "image_url", "image_url", Map.of("url", "data:" + photo.mimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(photoBytes))));
        }
        prompt.add(Map.of("role", "user", "content", turn));
        while (prompt.size() > 2 && client.estimate(prompt) > client.inputBudget()) prompt.remove(1);
        String reply;
        try {
            reply = client.reply("chat", prompt, photo != null);
        } catch (AiClient.Failure error) {
            throw error(HttpStatus.BAD_GATEWAY, error.visionUnsupported() ? error.getMessage()
                    : "No pudimos responder en este momento. Inténtalo de nuevo.");
        }
        String path = photo == null ? null : "progress/" + plant.publicId() + "/" + UUID.randomUUID() + "." + photo.extension();
        Path target = path == null ? null : files.resolve(path);
        try {
            if (target != null) {
                Files.createDirectories(target.getParent());
                Files.write(target, photoBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
            transaction.executeWithoutResult(status -> {
                lockAccount(user);
                String userMessage = insertMessage(user, session, "user", question);
                if (photo != null) {
                    jdbc.update("""
                            INSERT INTO imagenes_progreso_chat
                            (id,user_id,plant_id,message_id,relative_path,mime_type,file_size,caption,created_at)
                            VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                            """, UserPlants.newId(), user, plant.id(), userMessage, path, photo.mimeType(),
                            photoBytes.length, caption);
                }
                insertMessage(user, session, "assistant", reply);
            });
        } catch (IOException | RuntimeException failure) {
            if (target != null) try { Files.deleteIfExists(target); } catch (IOException e) { failure.addSuppressed(e); }
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la conversación.");
        }
        return Map.of("success", true, "reply", reply);
    }

    @GetMapping("/chat/history")
    public Map<String, Object> history(@RequestHeader(name = "Authorization", required = false) String token,
                                       @RequestParam(name = "plant_id", defaultValue = "") String plantId) {
        long user = accounts.requireUser(token);
        if (plantId.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Falta plant_id.");
        UserPlants.StoredPlant plant = plants.requirePlant(user, plantId.strip());
        var rows = jdbc.query("""
                SELECT m.id,m.role,m.content,m.created_at,
                  (SELECT p.id FROM imagenes_progreso_chat p WHERE p.message_id=m.id AND p.user_id=m.user_id ORDER BY p.created_at LIMIT 1)
                FROM mensajes_chat m WHERE m.user_id=? AND m.session_id=?
                ORDER BY m.created_at DESC,m.id DESC LIMIT 100
                """, (row, index) -> {
            var result = json.createObjectNode().put("id", UserPlants.publicId(row.getString(1)))
                    .put("role", row.getString(2)).put("content", row.getString(3))
                    .put("created_at", UserPlants.date(row, "created_at"));
            String imageId = row.getString(5);
            return imageId == null ? result.putNull("image_url") : result.put("image_url", imageUrl(imageId));
        }, user, plant.sessionId());
        Collections.reverse(rows);
        return Map.of("success", true, "messages", rows);
    }

    @GetMapping("/chat/images")
    public Map<String, Object> images(@RequestHeader(name = "Authorization", required = false) String token,
                                      @RequestParam(name = "plant_id", defaultValue = "") String plantId) {
        long user = accounts.requireUser(token);
        UserPlants.StoredPlant plant = plants.requirePlant(user, plantId.strip());
        var rows = jdbc.query("""
                SELECT id,caption,created_at FROM imagenes_progreso_chat
                WHERE user_id=? AND plant_id=? ORDER BY created_at,id
                """, (row, index) -> Map.of("id", UserPlants.publicId(row.getString(1)),
                "image_url", imageUrl(row.getString(1)), "caption", row.getString(2),
                "created_at", UserPlants.date(row, "created_at")), user, plant.id());
        return Map.of("success", true, "images", rows);
    }

    @GetMapping("/chat/images/{imageId}")
    public ResponseEntity<FileSystemResource> image(@RequestHeader(name = "Authorization", required = false) String token,
                                                     @PathVariable String imageId) {
        long user = accounts.requireUser(token);
        return jdbc.query("""
                SELECT i.relative_path,i.mime_type FROM imagenes_progreso_chat i
                JOIN identificaciones_planta p ON p.id=i.plant_id
                WHERE i.id=? AND i.user_id=? AND p.user_id=?
                """, (row, index) -> files.response(row.getString(1), row.getString(2)),
                UserPlants.databaseId(imageId), user, user).stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No se encontró esa imagen en tu cuenta."));
    }

    @GetMapping("/chats")
    public Map<String, Object> conversations(@RequestHeader(name = "Authorization", required = false) String token) {
        long user = accounts.requireUser(token);
        var sessions = jdbc.queryForList("SELECT DISTINCT session_id FROM mensajes_chat WHERE user_id=?", String.class, user);
        return Map.of("plant_ids", sessions.stream().filter(id -> id.startsWith("plant_"))
                .map(id -> id.substring("plant_".length())).toList());
    }

    @DeleteMapping("/chat")
    public ResponseEntity<Void> deleteChat(@RequestHeader(name = "Authorization", required = false) String token,
                                           @RequestParam("plant_id") String plantId) {
        long user = accounts.requireUser(token);
        var plant = plants.requirePlant(user, plantId.strip());
        deleteHistory(user, plant);
        return ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }

    @DeleteMapping("/chats")
    public ResponseEntity<Void> deleteAllChats(@RequestHeader(name = "Authorization", required = false) String token,
                                               @RequestParam(defaultValue = "false") boolean confirm) {
        long user = accounts.requireUser(token);
        if (!confirm) throw error(HttpStatus.BAD_REQUEST, "Confirma la eliminación de todos los chats.");
        deleteHistory(user, null);
        return ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore()).build();
    }

    private void deleteHistory(long user, UserPlants.StoredPlant plant) {
        var staged = new java.util.LinkedHashMap<Path,Path>();
        try {
            transaction.executeWithoutResult(status -> {
                lockAccount(user);
                var paths = plant == null
                        ? jdbc.queryForList("SELECT relative_path FROM imagenes_progreso_chat WHERE user_id=?", String.class, user)
                        : jdbc.queryForList("SELECT relative_path FROM imagenes_progreso_chat WHERE user_id=? AND plant_id=?",
                            String.class, user, plant.id());
                for (String path : paths) {
                    if (!path.matches("progress/[a-fA-F0-9-]{36}/[a-fA-F0-9-]{36}\\.(?:jpeg|jpg|png)")) {
                        throw error(HttpStatus.CONFLICT, "La ruta de una imagen de progreso no es válida.");
                    }
                    if (!Files.exists(files.resolve(path), java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                    Path source = files.existing(path);
                    Path hidden = source.resolveSibling(".deleted-" + UUID.randomUUID());
                    try {
                        Files.move(source, hidden, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        staged.put(source, hidden);
                    } catch (IOException e) {
                        throw error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar la eliminación de las fotos.");
                    }
                }
                if (plant == null) {
                    jdbc.update("DELETE FROM imagenes_progreso_chat WHERE user_id=?", user);
                    jdbc.update("DELETE FROM mensajes_chat WHERE user_id=?", user);
                } else {
                    jdbc.update("DELETE FROM imagenes_progreso_chat WHERE user_id=? AND plant_id=?", user, plant.id());
                    jdbc.update("DELETE FROM mensajes_chat WHERE user_id=? AND session_id=?", user, plant.sessionId());
                }
            });
        } catch (RuntimeException failure) {
            staged.forEach((source, hidden) -> {
                try { Files.move(hidden, source, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException e) { failure.addSuppressed(e); }
            });
            throw failure;
        }
        for (Path hidden : staged.values()) {
            try { Files.deleteIfExists(hidden); }
            catch (IOException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo purgar una foto aislada."); }
        }
    }

    private void lockAccount(long user) {
        jdbc.queryForObject("SELECT id FROM auth_user WHERE id=? FOR UPDATE", Long.class, user);
    }

    private String insertMessage(long user, String session, String role, String content) {
        String id = UserPlants.newId();
        jdbc.update("""
                INSERT INTO mensajes_chat (id,user_id,session_id,role,content,created_at)
                VALUES (?,?,?,?,?,UTC_TIMESTAMP(6))
                """, id, user, session, role, content);
        return id;
    }

    private static byte[] readImage(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No se pudo leer la imagen.");
        }
    }

    private static String context(UserPlants.StoredPlant plant) {
        return "Nombre científico: " + plant.scientific() + "\nNombre común: " + plant.common()
                + "\nFamilia: " + plant.family() + "\nSimilitud: " + (int)(plant.confidence() * 100) + "%";
    }

    private static String imageUrl(String databaseId) {
        return "agronomia-api/api/chat/images/" + UserPlants.publicId(databaseId);
    }

    private static ApiException error(HttpStatus status, String message) {
        return new ApiException(status, message);
    }
}
