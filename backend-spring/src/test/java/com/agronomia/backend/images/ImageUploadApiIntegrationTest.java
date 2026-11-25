package com.agronomia.backend.images;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.agronomia.backend.user.UserTokenService;
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
    @Autowired UserTokenService tokens;
    @MockitoBean PlantImageRepository repository;
    @Value("${plant.storage.directory}") String storageDirectory;
    private String auth;

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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS usuarios (id BIGINT PRIMARY KEY, username VARCHAR(150), password VARCHAR(128), email VARCHAR(254), is_active BOOLEAN DEFAULT TRUE)");
        jdbcTemplate.update("MERGE INTO usuarios (id,username,password,email,is_active) KEY(id) VALUES (1,'tester','x','t@test.local',TRUE)");
        auth = "Bearer " + tokens.issue(1L, "tester");
    }

    @AfterEach
    void cleanStorage() throws Exception {
        clearStorage();
    }

    @Test
    void preservesAndroidUploadContractAndPersistsOneImageAndRelativePath() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image("flower.jpg", "image/jpeg", "png")).header("Authorization", auth))
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
    void rejectsGifWithAConsistentJsonError() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).file(image("plant.jpg", "image/jpeg", "gif")).header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("JPEG, PNG y WebP")));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plant_images", Integer.class)).isZero();
        assertThat(countStoredFiles()).isZero();
    }

    @Test
    void missingPhotosProducesBadRequestJsonAndGetRemainsMethodNotAllowed() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get(ENDPOINT).header("Authorization", auth))
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
