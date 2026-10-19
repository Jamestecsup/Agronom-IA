package com.agronomia.backend.ai;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
public class ChatImageValidator {
    public static final int MAX_IMAGES = 5;
    public static final int MAX_IMAGE_BYTES = 15 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 50 * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;
    private static final Map<String, String> FORMATS = Map.of(
            "image/jpeg", "jpeg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

    public String validate(ChatContracts.ChatMessage message) {
        if (message.images() == null || message.images().isEmpty()) return null;
        if (!"user".equals(message.role())) return "Solo los mensajes del usuario pueden incluir imágenes.";
        if (message.images().size() > MAX_IMAGES) return "Adjunta un máximo de 5 imágenes por mensaje.";
        long totalBytes = 0;
        for (ChatContracts.ChatImage image : message.images()) {
            if (image == null || image.data() == null || image.data().length == 0)
                return "Una imagen está vacía o no se recibió correctamente.";
            if (image.data().length > MAX_IMAGE_BYTES)
                return "Cada imagen debe ocupar como máximo 15 MiB.";
            totalBytes += image.data().length;
            if (!FORMATS.containsKey(image.mimeType()))
                return "Formato no admitido. Se aceptan JPEG, PNG, WebP y GIF decodificables.";
            if (!safeFileName(image.fileName())) return "El nombre de una imagen no es válido.";
            String error = validateBytes(image.data(), image.mimeType());
            if (error != null) return error;
        }
        if (totalBytes > MAX_TOTAL_BYTES) return "Las imágenes superan el límite conjunto de 50 MiB.";
        return null;
    }

    private String validateBytes(byte[] bytes, String mimeType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return "No se pudo leer el contenido de una imagen.";
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return "Una imagen está dañada o no se puede decodificar.";
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String expected = FORMATS.get(mimeType);
                String actual = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(expected.equals(actual) || (expected.equals("jpeg") && actual.equals("jpg"))))
                    return "El contenido real no coincide con el formato declarado.";
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 16 || height < 16 || width > MAX_DIMENSION || height > MAX_DIMENSION)
                    return "Las dimensiones admitidas van de 16 a 4 096 píxeles.";
                var decoded = reader.read(0);
                if (decoded == null) return "Una imagen está dañada y no se pudo decodificar.";
                decoded.flush();
                return null;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException error) {
            return "Una imagen está dañada y no se pudo decodificar.";
        }
    }

    private boolean safeFileName(String name) {
        return name != null && !name.isBlank() && name.length() <= 180 && !name.contains("/") && !name.contains("\\");
    }
}
