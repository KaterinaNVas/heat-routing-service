package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.lct.heatrouting.importdata.DatasetReadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
public class MultiOksPreviewController {
    private final MultiOksPreviewService service;

    public MultiOksPreviewController(MultiOksPreviewService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/route/preview-all", consumes = "multipart/form-data",
            produces = "application/geo+json")
    public ObjectNode preview(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Файл GeoJSON пуст");
        Path temporary = Files.createTempFile("heat-preview-all-", ".geojson");
        try {
            try (java.io.InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return service.calculate(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            DatasetReadException.class})
    public ResponseEntity<Map<String, String>> invalidInput(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", exception.getMessage() == null ? "Ошибка расчёта" : exception.getMessage()));
    }
}
