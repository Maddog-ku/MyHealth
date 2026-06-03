package com.myhealth.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir Path tempDir;

    @Test
    void storeMealImage_acceptsValidPng_andStoresUnderDatePath() throws Exception {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "meal.png", "image/png", pngBytes(16, 12));

        String storagePath = service.storeMealImage(image, LocalDate.of(2026, 5, 30));

        assertThat(storagePath).startsWith("2026/05/30/");
        assertThat(storagePath).endsWith(".png");
        assertThat(Files.isRegularFile(tempDir.resolve(storagePath))).isTrue();
    }

    @Test
    void storeMealImage_rejectsSpoofedImageContent() {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "meal.png", "image/png", "not a png".getBytes());

        assertThatThrownBy(() -> service.storeMealImage(image, LocalDate.of(2026, 5, 30)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(api.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                });
    }

    @Test
    void storeMealImage_rejectsOversizedDimensions() throws Exception {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "wide.png", "image/png", pngBytes(8_001, 1));

        assertThatThrownBy(() -> service.storeMealImage(image, LocalDate.of(2026, 5, 30)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                    assertThat(api.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
                });
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Jwt("test-secret-test-secret-test-secret-32bytes!!", 15, 30),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Ai("local", "http://localhost:11434", "gemma4:e4b", "gemma4:e4b", 60),
                tempDir.toString());
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
