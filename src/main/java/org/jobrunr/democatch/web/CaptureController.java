package org.jobrunr.democatch.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.jobrunr.democatch.catalog.CardCatalog;
import org.jobrunr.democatch.config.AppPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CaptureController {

    private final CardCatalog cards;
    private final AppPaths paths;

    public CaptureController(CardCatalog cards, AppPaths paths) {
        this.cards = cards;
        this.paths = paths;
    }

    @GetMapping("/api/cards")
    public Object cards() {
        return cards.all();
    }

    @PostMapping(value = "/api/captures/{cardId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> capture(@PathVariable String cardId,
                                     @RequestPart("image") MultipartFile image) throws IOException {
        if (!cards.exists(cardId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown card " + cardId);
        }
        Path dir = paths.captures().resolve(cardId);
        Files.createDirectories(dir);
        Files.write(dir.resolve(Instant.now().toEpochMilli() + ".jpg"), image.getBytes());
        try (var files = Files.list(dir)) {
            return Map.of("count", files.filter(p -> p.toString().endsWith(".jpg")).count());
        }
    }
}
