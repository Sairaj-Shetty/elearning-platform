package com.lumina.media;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Path;

public interface MediaStorage {
    String store(String key, Path sourceFile);
    String store(String key, InputStream input);
    Resource load(String key);
    void delete(String key);
    Path root();
}
