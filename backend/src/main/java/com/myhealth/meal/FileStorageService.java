package com.myhealth.meal;

import com.myhealth.common.ApiException;
import com.myhealth.common.ErrorCode;
import com.myhealth.config.AppProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;

    public FileStorageService(AppProperties properties) {
        this.uploadRoot = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    public String storeMealImage(MultipartFile file, LocalDate date) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validate(file);
        String extension = extension(file.getContentType());
        Path dir = uploadRoot.resolve(Path.of(
                String.valueOf(date.getYear()),
                "%02d".formatted(date.getMonthValue()),
                "%02d".formatted(date.getDayOfMonth())));
        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store uploaded image", ex);
        }
        return "%d/%02d/%02d/%s".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), filename);
    }

    public StoredFile load(String storagePath) {
        Path file = resolveStoragePath(storagePath);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Image not found");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            return new StoredFile(resource, Files.probeContentType(file));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load stored image", ex);
        }
    }

    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoragePath(storagePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete stored image", ex);
        }
    }

    private void validate(MultipartFile file) {
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded file is too large");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported image type");
        }
        try {
            byte[] bytes = file.getBytes();
            byte[] header = new ByteArrayInputStream(bytes).readNBytes(16);
            if (!looksLikeImage(mime, header)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Uploaded file is not a supported image");
            }
            if (requiresImageIoDecode(mime) && ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Uploaded file is not a valid image");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to inspect uploaded image", ex);
        }
    }

    private boolean looksLikeImage(String mime, byte[] header) throws IOException {
        String hex = HexFormat.of().formatHex(header).toLowerCase(Locale.ROOT);
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> hex.startsWith("ffd8ff");
            case "image/png" -> hex.startsWith("89504e470d0a1a0a");
            case "image/webp" -> header.length >= 12
                    && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    private boolean requiresImageIoDecode(String mime) {
        String normalized = mime.toLowerCase(Locale.ROOT);
        return normalized.equals("image/jpeg") || normalized.equals("image/png");
    }

    private Path resolveStoragePath(String storagePath) {
        Path file = uploadRoot.resolve(storagePath).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Invalid storage path");
        }
        return file;
    }

    private String extension(String mime) {
        return switch (mime == null ? "" : mime.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public record StoredFile(Resource resource, String contentType) {
    }
}
