package com.agronomia.backend.images;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {
    private final ImageValidator validator = new ImageValidator();

    @Test
    void detectsPngFromBytesInsteadOfFilenameOrMimeHeader() throws Exception {
        var file = multipart("plant.jpg", "image/jpeg", imageBytes("png"));

        var validated = validator.validate(file);

        assertThat(validated.mimeType()).isEqualTo("image/png");
        assertThat(validated.extension()).isEqualTo("png");
        assertThat(validated.width()).isEqualTo(24);
        assertThat(validated.height()).isEqualTo(20);
    }

    @Test
    void acceptsJpegAndWebpWhenTheyCanBeDecoded() throws Exception {
        assertThat(validator.validate(multipart("plant.png", "image/png", imageBytes("jpeg"))).mimeType())
                .isEqualTo("image/jpeg");
        assertThat(validator.validate(multipart("plant.jpg", "image/jpeg", imageBytes("webp"))).mimeType())
                .isEqualTo("image/webp");
    }

    @Test
    void rejectsGifAsUnsupportedContent() throws Exception {
        assertThatThrownBy(() -> validator.validate(multipart("plant.jpg", "image/jpeg", imageBytes("gif"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("JPEG, PNG y WebP");
    }

    @Test
    void rejectsCorruptBytesThatPretendToBePng() {
        byte[] corrupt = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        assertThatThrownBy(() -> validator.validate(multipart("not-really.png", "image/png", corrupt)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("decodificar");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(multipart("empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vacía");
    }

    @Test
    void rejectsFilesAboveTheLegacyFifteenMebibyteRule() {
        byte[] oversized = new byte[(int) ImageValidator.MAX_FILE_BYTES + 1];
        assertThatThrownBy(() -> validator.validate(multipart("large.jpg", "image/jpeg", oversized)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("15 MiB");
    }

    private static MockMultipartFile multipart(String name, String contentType, byte[] content) {
        return new MockMultipartFile("photos[]", name, contentType, content);
    }

    private static byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(24, 20, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336633);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) throw new IllegalStateException("No ImageIO writer for " + format);
        return output.toByteArray();
    }
}
