package com.agronomia.backend.images;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "plant.storage.directory=target/integration-image-storage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadApiIntegrationTest {
    private static final String ENDPOINT = "/agronomia-api/api/images.php";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean PlantImageRepository repository;
    @Value("${plant.storage.directory}") String storageDirectory;

    @BeforeEach
    void setUp() throws Exception {
        reset(repository);
        PlantImageRepository jdbcRepository = new PlantImageRepository(jdbcTemplate);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") List<PlantImageRecord> rows = invocation.getArgument(0);
            jdbcRepository.insertAll(rows);
            return null;
        }).when(repository).insertAll(anyList());
        jdbcTemplate.update("DELETE FROM plant_images");
        clearStorage();
    }

    @AfterEach
    void cleanStorage() throws Exception {
        clearStorage();
    }

    @Test
    void preservesAndroidUploadContractAndPersistsOneImageAndRelativePath() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image("flower.jpg", "image/jpeg", "png")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.batch_id").isNotEmpty())
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].mime_type").value("image/png"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT relative_path FROM plant_images", String.class)).endsWith(".png");
        assertThat(countStoredFiles()).isEqualTo(1);
    }

    @Test
    void acceptsTheMaximumFiveImagesAndPersistsTheWholeBatch() throws Exception {
        var request = multipart(ENDPOINT);
        for (int index = 0; index < 5; index++) {
            request.file(image("plant-" + index + ".jpg", "image/jpeg", "jpeg"));
        }

        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(5));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isEqualTo(5);
        assertThat(countStoredFiles()).isEqualTo(5);
    }

    @Test
    void rejectsMoreThanFiveBeforeWritingFilesOrRows() throws Exception {
        var request = multipart(ENDPOINT);
        for (int index = 0; index < 6; index++) request.file(image("plant-" + index + ".jpg", "image/jpeg", "jpeg"));

        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isZero();
        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void rejectsGifWithAConsistentJsonError() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image("plant.jpg", "image/jpeg", "gif")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("JPEG, PNG y WebP")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isZero();
        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void acceptsDecodableWebpAndUsesItsDetectedExtension() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image("plant.png", "image/png", "webp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].mime_type").value("image/webp"));

        assertThat(jdbcTemplate.queryForObject("SELECT relative_path FROM plant_images", String.class)).endsWith(".webp");
    }

    @Test
    void rejectsCorruptContentEvenWhenItHasAnAllowedSignature() throws Exception {
        byte[] corruptPng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        mockMvc.perform(multipart(ENDPOINT).file(new MockMultipartFile("photos[]", "flower.png", "image/png", corruptPng)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isZero();
        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void cleansFilesWhenDatabaseInsertionFails() throws Exception {
        PlantImageRepository jdbcRepository = new PlantImageRepository(jdbcTemplate);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") List<PlantImageRecord> rows = invocation.getArgument(0);
            jdbcRepository.insertAll(rows.subList(0, 1));
            throw new org.springframework.dao.DataIntegrityViolationException("test failure after the first insert");
        }).when(repository).insertAll(anyList());

        mockMvc.perform(multipart(ENDPOINT)
                        .file(image("flower-a.jpg", "image/jpeg", "jpeg"))
                        .file(image("flower-b.jpg", "image/jpeg", "jpeg")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("No se completó el almacenamiento")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isZero();
        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void missingPhotosProducesBadRequestJsonAndGetRemainsMethodNotAllowed() throws Exception {
        mockMvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static MockMultipartFile image(String filename, String mime, String format) throws Exception {
        BufferedImage image = new BufferedImage(24, 20, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x2F6049);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, bytes)) throw new IllegalStateException("No ImageIO writer for " + format);
        return new MockMultipartFile("photos[]", filename, mime, bytes.toByteArray());
    }

    private int countStoredFiles() throws Exception {
        Path root = Path.of(storageDirectory);
        if (!Files.exists(root)) return 0;
        try (var paths = Files.walk(root)) {
            return (int) paths.filter(Files::isRegularFile).count();
        }
    }

    private void clearStorage() throws Exception {
        Path root = Path.of(storageDirectory);
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
