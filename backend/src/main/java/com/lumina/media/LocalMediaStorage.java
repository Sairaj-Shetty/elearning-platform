package com.lumina.media;

import com.lumina.common.ApiException;
import com.lumina.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.stream.Stream;

@Service
public class LocalMediaStorage implements MediaStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalMediaStorage.class);
    private final Path root;

    public LocalMediaStorage(AppProperties props) {
        this.root = Path.of(props.media().root()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        sweepUploading();
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public String store(String key, Path sourceFile) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The file could not be saved.");
        }
    }

    @Override
    public String store(String key, InputStream input) {
        Path target = resolve(key);
        Path temp = target.resolveSibling(target.getFileName() + ".uploading");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                log.warn("Could not delete temporary upload {}", temp);
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "The file could not be saved.");
        }
    }

    @Override
    public Resource load(String key) {
        Path target = resolve(key);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_MISSING", "The stored file could not be found.");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not delete media key {}", key);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PATH", "Invalid storage path.");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PATH", "Invalid storage path.");
        }
        return target;
    }

    private void sweepUploading() {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.getFileName().toString().endsWith(".uploading")).forEach(path -> {
                try {
                    Instant modified = Files.getLastModifiedTime(path).toInstant();
                    if (modified.isBefore(Instant.now().minusSeconds(3600))) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                    log.debug("Skip sweep for {}", path);
                }
            });
        } catch (IOException ex) {
            log.debug("Media sweep skipped: {}", ex.getMessage());
        }
    }
}
