package com.agronomia.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "plant.storage.directory=target/integration-chat-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserChatApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @MockBean UserTokenService tokens;
    @MockBean UserChatService chat;

    @Test
    void historyEndpointKeepsPlantAndMessageContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(chat.history(7L, "plant-1")).thenReturn(Map.of(
                "success", true,
                "plant", Map.of("id", "plant-1", "scientific_name", "Rosa canina"),
                "messages", List.of(Map.of(
                        "id", "message-1", "role", "assistant", "content", "Cuida el riego.",
                        "created_at", "2026-01-01T10:00", "image_url", "agronomia-api/api/chat/images/image-1"))
        ));

        mockMvc.perform(get("/agronomia-api/api/chat/history")
                        .param("plant_id", "plant-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.plant.scientific_name").value("Rosa canina"))
                .andExpect(jsonPath("$.messages[0].role").value("assistant"))
                .andExpect(jsonPath("$.messages[0].image_url").value("agronomia-api/api/chat/images/image-1"));

        verify(chat).history(7L, "plant-1");
    }

    @Test
    void historyEndpointStillRequiresUserAuthentication() throws Exception {
        mockMvc.perform(get("/agronomia-api/api/chat/history").param("plant_id", "plant-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void progressImagesEndpointKeepsListContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(chat.progressImages(7L, "plant-1")).thenReturn(List.of(Map.of(
                "id", "image-1", "image_url", "agronomia-api/api/chat/images/image-1",
                "caption", "Hoja nueva", "created_at", "2026-01-01T10:00")));

        mockMvc.perform(get("/agronomia-api/api/chat/images")
                        .param("plant_id", "plant-1")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.images[0].id").value("image-1"))
                .andExpect(jsonPath("$.images[0].caption").value("Hoja nueva"))
                .andExpect(jsonPath("$.images[0].image_url").value("agronomia-api/api/chat/images/image-1"));

        verify(chat).progressImages(7L, "plant-1");
    }

    @Test
    void progressImageEndpointReturnsOwnedFileBytesAndMimeType() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(chat.progressImage(7L, "image-1")).thenReturn(Map.of(
                "relative_path", "progress/plant-1/image.png", "mime_type", "image/png"));
        Path file = Path.of("target/integration-chat-storage/progress/plant-1/image.png");
        Files.createDirectories(file.getParent());
        byte[] bytes = new byte[]{1, 3, 5, 7};
        Files.write(file, bytes);

        try {
            mockMvc.perform(get("/agronomia-api/api/chat/images/image-1")
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                    .andExpect(content().bytes(bytes));
        } finally {
            Files.deleteIfExists(file);
        }
        verify(chat).progressImage(7L, "image-1");
    }

    @Test
    void webUserMultipartPostKeepsReplyContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        when(chat.reply(7L, "plant-1", "¿Necesita agua?", null, "image/jpeg", "¿Necesita agua?"))
                .thenReturn(Map.of("success", true, "reply", "Revisa primero la humedad del suelo."));

        mockMvc.perform(multipart("/agronomia-api/api/chat")
                        .param("plant_id", "plant-1")
                        .param("message", "¿Necesita agua?")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reply").value("Revisa primero la humedad del suelo."));

        verify(chat).reply(7L, "plant-1", "¿Necesita agua?", null, "image/jpeg", "¿Necesita agua?");
    }

    @Test
    void androidMultipartPostKeepsImageChatContract() throws Exception {
        when(tokens.verify("valid-token")).thenReturn(Optional.of(new UserTokenService.UserSession(7L, "tester")));
        byte[] bytes = new byte[]{2, 4, 6};
        MockMultipartFile image = new MockMultipartFile("image", "progress.png", "image/png", bytes);
        when(chat.reply(eq(7L), eq("plant-1"), eq("Mira esta hoja"), aryEq(bytes),
                eq("image/png"), eq("Mira esta hoja")))
                .thenReturn(Map.of("success", true, "reply", "Veo una hoja nueva."));

        mockMvc.perform(multipart("/agronomia-api/api/chat")
                        .file(image)
                        .param("plant_id", "plant-1")
                        .param("message", "Mira esta hoja")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reply").value("Veo una hoja nueva."));

        verify(chat).reply(eq(7L), eq("plant-1"), eq("Mira esta hoja"), aryEq(bytes),
                eq("image/png"), eq("Mira esta hoja"));
    }
}
