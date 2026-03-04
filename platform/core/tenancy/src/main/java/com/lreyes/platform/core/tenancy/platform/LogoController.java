package com.lreyes.platform.core.tenancy.platform;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Sirve imágenes de logotipo de tenants desde {@code data/logos/}.
 */
@RestController
@RequestMapping("/api/logos")
public class LogoController {

    private static final Path LOGOS_DIR = Paths.get("data", "logos");

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getLogo(@PathVariable String filename) {
        // Prevenir path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path file = LOGOS_DIR.resolve(filename);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "image/png";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (lower.endsWith(".svg")) {
            contentType = "image/svg+xml";
        } else if (lower.endsWith(".gif")) {
            contentType = "image/gif";
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }
}
