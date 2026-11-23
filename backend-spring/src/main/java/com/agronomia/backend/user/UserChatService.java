package com.agronomia.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserChatService {
    private final UserPlantService plants;
    private final UserAiService ai;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final Path uploadDir;

    public UserChatService(UserPlantService plants, UserAiService ai, org.springframework.jdbc.core.JdbcTemplate jdbc,
                           @Value("${plant.storage.directory:${PLANT_UPLOAD_DIR:C:/xampp/plant-images}}") String uploadDir) {
        this.plants = plants; this.ai = ai; this.jdbc = jdbc; this.uploadDir = Path.of(uploadDir);
    }

    public Map<String, Object> history(long userId, String plantId) {
        Map<String, Object> plant = plants.findOwnedPlant(userId, plantId);
        String sessionId = "plant-" + plantId;
        List<Map<String, Object>> msgs = jdbc.query(
                "SELECT m.id,m.role,m.content,m.created_at,(SELECT pi.id FROM imagenes_progreso_chat pi WHERE pi.message_id=m.id LIMIT 1) AS progress_id " +
                        "FROM mensajes_chat m WHERE m.user_id=? AND m.session_id=? ORDER BY m.created_at ASC",
                (rs, i) -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("id", rs.getString("id"));
                    msg.put("role", rs.getString("role"));
                    msg.put("content", rs.getString("content"));
                    msg.put("created_at", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    String progressId = rs.getString("progress_id");
                    msg.put("image_url", progressId == null ? null : "agronomia-api/api/chat/images/" + progressId);
                    return msg;
                },
                userId, sessionId);
        return Map.of("success", true, "plant", plantSummary(plant), "messages", msgs);
    }

    public Map<String, Object> reply(long userId, String plantId, String message, byte[] imageData, String imageMime, String imageCaption) {
        if (plantId == null || plantId.isBlank()) throw new UserApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Falta plant_id para asociar el chat a una planta.");
        if ((message == null || message.isBlank()) && (imageData == null || imageData.length == 0)) {
            throw new UserApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Escribe un mensaje o adjunta una foto.");
        }
        String userMessage = (message == null || message.isBlank())
                ? "Comparto una foto del progreso de mi planta. ¿Qué observas y qué cuidado me recomiendas?"
                : message.trim();
        Map<String, Object> plant = plants.findOwnedPlant(userId, plantId);
        String sessionId = "plant-" + plantId;
        String context = buildContext(plant);
        String skill = ai.loadSkill("chat-assistant");

        List<Map<String, Object>> history = jdbc.query("SELECT role,content FROM mensajes_chat WHERE user_id=? AND session_id=? ORDER BY created_at ASC",
                (rs,i) -> Map.of("role", rs.getString("role"), "content", rs.getString("content")), userId, sessionId);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role","system","content", List.of(Map.of("type","text","text", skill))));
        int start = Math.max(0, history.size()-12);
        for (int i=start; i<history.size(); i++) {
            Map<String,Object> h = history.get(i);
            messages.add(Map.of("role", h.get("role"), "content", List.of(Map.of("type","text","text", String.valueOf(h.get("content"))))));
        }
        List<Object> userContent = new ArrayList<>();
        userContent.add(Map.of("type","text","text", userMessage + "\n\n" + context));
        if (imageData != null && imageData.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageData);
            userContent.add(Map.of("type","image_url","image_url", Map.of("url","data:"+(imageMime==null?"image/jpeg":imageMime)+";base64,"+b64)));
        }
        messages.add(Map.of("role","user","content", userContent));

        String reply = ai.chatReply(messages, 0.2);
        String userMsgId = UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO mensajes_chat (id,user_id,session_id,role,content,created_at) VALUES (?,?,?,?,?,?)",
                userMsgId, userId, sessionId, "user", userMessage, Timestamp.valueOf(LocalDateTime.now()));
        if (imageData != null && imageData.length > 0) {
            saveProgressImage(userId, plantId, userMsgId, imageData, imageMime, imageCaption == null ? "" : imageCaption.trim());
        }
        jdbc.update("INSERT INTO mensajes_chat (id,user_id,session_id,role,content,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString().replace("-",""), userId, sessionId, "assistant", reply, Timestamp.valueOf(LocalDateTime.now()));
        return Map.of("success", true, "reply", reply);
    }

    public List<Map<String, Object>> progressImages(long userId, String plantId) {
        plants.findOwnedPlant(userId, plantId);
        return jdbc.query("SELECT id,caption,created_at FROM imagenes_progreso_chat WHERE user_id=? AND plant_id=? ORDER BY created_at ASC",
                (rs,i) -> {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("image_url", "agronomia-api/api/chat/images/" + rs.getString("id"));
                    m.put("caption", rs.getString("caption"));
                    m.put("created_at", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return m;
                }, userId, toDbId(plantId));
    }

    private Object toDbId(String id) {
        if (id == null) return null;
        String hex = id.replace("-", "");
        return hex.length() == 32 ? hex : id;
    }

    public Map<String, Object> progressImage(long userId, String imageId) {
        List<Map<String,Object>> rows = jdbc.query("SELECT relative_path,mime_type FROM imagenes_progreso_chat WHERE id=? AND user_id=?",
                (rs,i) -> Map.of("relative_path", rs.getString("relative_path"), "mime_type", rs.getString("mime_type")),
                toDbId(imageId), userId);
        if (rows.isEmpty()) throw new UserApiException(org.springframework.http.HttpStatus.NOT_FOUND, "No se encontro esa imagen en tu cuenta.");
        return rows.get(0);
    }

    private void saveProgressImage(long userId, String plantId, String messageId, byte[] data, String mime, String caption) {
        String ext = mime != null && mime.toLowerCase().contains("png") ? "png" : "jpg";
        String relative = "progress/" + plantId + "/" + UUID.randomUUID() + "." + ext;
        Path abs = uploadDir.resolve(relative).normalize();
        if (!abs.startsWith(uploadDir.normalize())) throw new UserApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Ruta de imagen invalida.");
        try {
            Files.createDirectories(abs.getParent());
            Files.write(abs, data);
        } catch (Exception e) {
            throw new UserApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen de progreso.");
        }
        jdbc.update("INSERT INTO imagenes_progreso_chat (id,user_id,plant_id,message_id,relative_path,mime_type,file_size,caption,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString().replace("-",""), userId, toDbId(plantId), toDbId(messageId), relative,
                mime == null ? "image/jpeg" : mime, (long) data.length, caption == null ? "" : caption, Timestamp.valueOf(LocalDateTime.now()));
    }

    private String buildContext(Map<String, Object> plant) {
        Map<String,Object> result = plants.asMap(plant.get("result_json"));
        StringBuilder sb = new StringBuilder("Contexto de la planta identificada:\n");
        sb.append("- Nombre comun: ").append(result.getOrDefault("common_name", plant.get("common_name"))).append("\n");
        sb.append("- Nombre cientifico: ").append(result.getOrDefault("scientific_name", plant.get("scientific_name"))).append("\n");
        sb.append("- Familia: ").append(result.getOrDefault("family", "")).append("\n");
        Object sections = result.get("sections");
        if (sections instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?,?> s)) continue;
                sb.append("\n## ").append(s.get("title")).append("\n").append(s.get("body")).append("\n");
            }
        }
        return sb.toString();
    }

    private Map<String, Object> plantSummary(Map<String, Object> plant) {
        Map<String,Object> result = plants.asMap(plant.get("result_json"));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", plant.get("id"));
        out.put("scientific_name", result.getOrDefault("scientific_name", plant.get("scientific_name")));
        out.put("common_name", result.getOrDefault("common_name", plant.get("common_name")));
        out.put("created_at", plant.get("created_at"));
        return out;
    }
}
