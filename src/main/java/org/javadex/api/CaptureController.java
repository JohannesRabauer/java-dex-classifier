package org.javadex.api;

import org.javadex.JavaDexApplicationConfiguration;
import org.javadex.catalog.CardCatalog;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

@RestController
public class CaptureController {

    private final CardCatalog cards;
    private final JavaDexApplicationConfiguration config;

    public CaptureController(CardCatalog cards, JavaDexApplicationConfiguration config) {
        this.cards = cards;
        this.config = config;
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
        Path dir = config.capturesPath().resolve(cardId);
        Files.createDirectories(dir);
        Files.write(dir.resolve(Instant.now().toEpochMilli() + ".jpg"), image.getBytes());
        try (var files = Files.list(dir)) {
            return Map.of("count", files.filter(p -> p.toString().endsWith(".jpg")).count());
        }
    }
}
