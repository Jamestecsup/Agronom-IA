package com.agronomia.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class UserChatService {
    private final UserPlantService plants;
    private final PlantAiClient ai;
    private final UserChatRepository chatRepository;
    private final Path uploadDir;

    public UserChatService(UserPlantService plants, PlantAiClient ai, UserChatRepository chatRepository,
                           @Value("${plant.storage.directory:${PLANT_UPLOAD_DIR:C:/xampp/plant-images}}") String uploadDir) {
        this.plants = plants; this.ai = ai; this.chatRepository = chatRepository; this.uploadDir = Path.of(uploadDir);
    }

    public Map<String, Object> history(long userId, String plantId) {
        Map<String, Object> plant = plants.findOwnedPlant(userId, plantId);
        String sessionId = "plant-" + plantId;
        List<Map<String, Object>> msgs = new ArrayList<>();
        for (UserChatRepository.ChatHistoryRow row : chatRepository.findHistory(userId, sessionId)) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("id", row.id());
            msg.put("role", row.role());
            msg.put("content", row.content());
            msg.put("created_at", row.createdAt());
            msg.put("image_url", row.progressImageId() == null
                    ? null : "agronomia-api/api/chat/images/" + row.progressImageId());
            msgs.add(msg);
        }
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

        List<UserChatRepository.PromptMessageRow> history = chatRepository.findPromptHistory(userId, sessionId);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role","system","content", List.of(Map.of("type","text","text", skill))));
        int start = Math.max(0, history.size()-12);
        for (int i=start; i<history.size(); i++) {
            UserChatRepository.PromptMessageRow h = history.get(i);
            messages.add(Map.of("role", h.role(), "content", List.of(Map.of("type","text","text", String.valueOf(h.content())))));
        }
        List<Object> userContent = new ArrayList<>();
        userContent.add(Map.of("type","text","text", userMessage + "\n\n" + context));
        if (imageData != null && imageData.length > 0) {
            String b64 = Base64.getEncoder().encodeToString(imageData);
            userContent.add(Map.of("type","image_url","image_url", Map.of("url","data:"+(imageMime==null?"image/jpeg":imageMime)+";base64,"+b64)));
        }
        messages.add(Map.of("role","user","content", userContent));

        String reply = ai.chatReply(messages, 0.2);
        String userMsgId = chatRepository.insertMessage(userId, sessionId, "user", userMessage);
        if (imageData != null && imageData.length > 0) {
            saveProgressImage(userId, plantId, userMsgId, imageData, imageMime, imageCaption == null ? "" : imageCaption.trim());
        }
        chatRepository.insertMessage(userId, sessionId, "assistant", reply);
        return Map.of("success", true, "reply", reply);
    }

    public List<Map<String, Object>> progressImages(long userId, String plantId) {
        plants.findOwnedPlant(userId, plantId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserChatRepository.ProgressImageRow image : chatRepository.findProgressImages(userId, plantId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", image.id());
            row.put("image_url", "agronomia-api/api/chat/images/" + image.id());
            row.put("caption", image.caption());
            row.put("created_at", image.createdAt());
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> progressImage(long userId, String imageId) {
        UserChatRepository.StoredProgressImage image = chatRepository.findProgressImage(userId, imageId)
                .orElseThrow(() -> new UserApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "No se encontro esa imagen en tu cuenta."));
        return Map.of("relative_path", image.relativePath(), "mime_type", image.mimeType());
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
        chatRepository.insertProgressImage(userId, plantId, messageId, relative,
                mime == null ? "image/jpeg" : mime, data.length, caption);
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
