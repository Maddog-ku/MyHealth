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

    @Test
    void storeMealImage_rejectsOversizedPixelCount_evenWhenEachDimensionFits() throws Exception {
        // 7000 * 6000 = 42M pixels > 40M cap, though neither side exceeds 8000px.
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "big.webp", "image/webp", webpVp8x(7_000, 6_000));

        assertThatThrownBy(() -> service.storeMealImage(image, LocalDate.of(2026, 5, 30)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void storeMealImage_acceptsValidWebp_extendedFormat() {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "x.webp", "image/webp", webpVp8x(16, 12));

        String storagePath = service.storeMealImage(image, LocalDate.of(2026, 5, 30));

        assertThat(storagePath).endsWith(".webp");
        assertThat(Files.isRegularFile(tempDir.resolve(storagePath))).isTrue();
    }

    @Test
    void storeMealImage_acceptsValidWebp_losslessFormat() {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "l.webp", "image/webp", webpVp8l(16, 12));

        assertThat(service.storeMealImage(image, LocalDate.of(2026, 5, 30))).endsWith(".webp");
    }

    @Test
    void storeMealImage_acceptsValidWebp_lossyFormat() {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "y.webp", "image/webp", webpVp8Lossy(16, 12));

        assertThat(service.storeMealImage(image, LocalDate.of(2026, 5, 30))).endsWith(".webp");
    }

    @Test
    void storeMealImage_rejectsOversizedWebpDimensions() {
        FileStorageService service = new FileStorageService(properties());
        MockMultipartFile image = new MockMultipartFile("image", "wide.webp", "image/webp", webpVp8x(8_001, 1));

        assertThatThrownBy(() -> service.storeMealImage(image, LocalDate.of(2026, 5, 30)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
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

    // Minimal WebP container headers, built from the WebP/VP8 bitstream spec so the
    // dimension parser is checked against an independent encoder, not its own decode math.
    private static final java.nio.charset.Charset ASCII = java.nio.charset.StandardCharsets.US_ASCII;

    private byte[] webpFrame(String chunkFourCc) {
        byte[] b = new byte[30];
        System.arraycopy("RIFF".getBytes(ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(ASCII), 0, b, 8, 4);
        System.arraycopy(chunkFourCc.getBytes(ASCII), 0, b, 12, 4);
        return b;
    }

    private byte[] webpVp8x(int width, int height) {
        byte[] b = webpFrame("VP8X");
        int w = width - 1;
        int h = height - 1;
        b[24] = (byte) (w & 0xff);
        b[25] = (byte) ((w >> 8) & 0xff);
        b[26] = (byte) ((w >> 16) & 0xff);
        b[27] = (byte) (h & 0xff);
        b[28] = (byte) ((h >> 8) & 0xff);
        b[29] = (byte) ((h >> 16) & 0xff);
        return b;
    }

    private byte[] webpVp8l(int width, int height) {
        byte[] b = webpFrame("VP8L");
        b[20] = 0x2f;
        int w = width - 1;   // 14-bit
        int h = height - 1;  // 14-bit
        b[21] = (byte) (w & 0xff);
        b[22] = (byte) (((w >> 8) & 0x3f) | ((h & 0x03) << 6));
        b[23] = (byte) ((h >> 2) & 0xff);
        b[24] = (byte) ((h >> 10) & 0x0f);
        return b;
    }

    private byte[] webpVp8Lossy(int width, int height) {
        byte[] b = webpFrame("VP8 ");
        b[23] = (byte) 0x9d;
        b[24] = 0x01;
        b[25] = 0x2a;
        b[26] = (byte) (width & 0xff);
        b[27] = (byte) ((width >> 8) & 0x3f);
        b[28] = (byte) (height & 0xff);
        b[29] = (byte) ((height >> 8) & 0x3f);
        return b;
    }
}
