package com.agronomia.backend.images;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class ImageValidator {
    public static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    public static final int MAX_DIMENSION = 4096;
    public static final int MIN_DIMENSION = 16;

    private static final Map<String, ImageType> SIGNATURES = Map.of(
            "jpeg", new ImageType("image/jpeg", "jpg"),
            "png", new ImageType("image/png", "png"),
            "webp", new ImageType("image/webp", "webp")
    );

    public ImageMetadata validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Una imagen está vacía o no se recibió correctamente.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Una imagen supera el límite de 15 MiB.");
        }

        ImageType type = detectType(file);
        try (InputStream input = file.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No se pudo leer el contenido de la imagen.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La imagen está dañada o no se puede decodificar.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!matches(type, actualFormat)) {
                    throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido no coincide con un formato admitido.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_DIMENSION || height < MIN_DIMENSION || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Las dimensiones deben estar entre 16 y 4 096 píxeles.");
                }
                var decoded = reader.read(0);
                if (decoded == null) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La imagen está dañada y no se pudo decodificar.");
                }
                decoded.flush();
                return new ImageMetadata(file, UUID.randomUUID().toString(), type.mimeType(), type.extension(), file.getSize(), width, height);
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "La imagen está dañada y no se pudo decodificar.");
        }
    }

    private ImageType detectType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length >= 3 && unsigned(header[0]) == 0xFF && unsigned(header[1]) == 0xD8 && unsigned(header[2]) == 0xFF) {
                return SIGNATURES.get("jpeg");
            }
            byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            if (startsWith(header, pngSignature)) {
                return SIGNATURES.get("png");
            }
            if (header.length >= 12 && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 12).equals("WEBP")) {
                return SIGNATURES.get("webp");
            }
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato no admitido. Se aceptan JPEG, PNG y WebP decodificables.");
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "No se pudo leer el archivo recibido.");
        }
    }

    private static boolean matches(ImageType type, String format) {
        return switch (type.mimeType()) {
            case "image/jpeg" -> format.equals("jpeg") || format.equals("jpg");
            case "image/png" -> format.equals("png");
            case "image/webp" -> format.equals("webp");
            default -> false;
        };
    }

    private static int unsigned(byte value) { return value & 0xFF; }
    private static String ascii(byte[] bytes, int start, int end) { return new String(bytes, start, end - start, java.nio.charset.StandardCharsets.US_ASCII); }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (data[index] != signature[index]) return false;
        }
        return true;
    }

    private record ImageType(String mimeType, String extension) { }
}
