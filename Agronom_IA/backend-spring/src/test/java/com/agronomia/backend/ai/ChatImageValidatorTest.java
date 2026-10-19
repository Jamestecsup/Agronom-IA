package com.agronomia.backend.ai;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatImageValidatorTest {
    private final ChatImageValidator validator = new ChatImageValidator();

    @Test
    void acceptsDecodablePngAndRejectsMimeSpoofingAndCorruptContent() throws Exception {
        byte[] png = pngFixture();
        assertThat(validator.validate(new ChatContracts.ChatMessage("user", "Identifica", List.of(
                new ChatContracts.ChatImage("planta.png", "image/png", png))))).isNull();
        assertThat(validator.validate(new ChatContracts.ChatMessage("user", "Identifica", List.of(
                new ChatContracts.ChatImage("falsa.jpg", "image/jpeg", png)))))
                .isEqualTo("El contenido real no coincide con el formato declarado.");
        assertThat(validator.validate(new ChatContracts.ChatMessage("user", "Identifica", List.of(
                new ChatContracts.ChatImage("corrupta.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47})))))
                .isEqualTo("Una imagen está dañada o no se puede decodificar.");
    }

    @Test
    void rejectsImagesOnAssistantMessagesAndMoreThanFiveImages() throws Exception {
        byte[] png = pngFixture();
        var image = new ChatContracts.ChatImage("planta.png", "image/png", png);
        assertThat(validator.validate(new ChatContracts.ChatMessage("assistant", "respuesta", List.of(image))))
                .isEqualTo("Solo los mensajes del usuario pueden incluir imágenes.");
        assertThat(validator.validate(new ChatContracts.ChatMessage("user", "", List.of(image, image, image, image, image, image))))
                .isEqualTo("Adjunta un máximo de 5 imágenes por mensaje.");
    }

    private byte[] pngFixture() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        image.setRGB(16, 16, 0x228833);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
