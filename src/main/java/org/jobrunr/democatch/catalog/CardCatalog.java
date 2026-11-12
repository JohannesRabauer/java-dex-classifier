package org.jobrunr.democatch.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CardCatalog {

    private final Path cardsDir;
    private volatile List<Card> cards = List.of();

    public CardCatalog(@Value("${app.cards-dir:cards}") String cardsDir) {
        this.cardsDir = Path.of(cardsDir);
        load();
    }

    private void load() {
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(cardsDir.resolve("cards.json")));
            this.cards = StreamSupport.stream(root.path("cards").spliterator(), false)
                    .map(c -> new Card(
                            c.path("id").asString(),
                            c.path("n").asInt(),
                            c.path("title").asString(),
                            c.path("type").asString()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + cardsDir.resolve("cards.json"), e);
        }
    }

    public List<Card> all() {
        return cards;
    }

    public Optional<Card> byId(String id) {
        return cards.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public boolean exists(String id) {
        return byId(id).isPresent();
    }

    public Optional<Path> pngFor(String id) {
        return byId(id).map(c -> cardsDir.resolve("png").resolve(c.id() + ".png"))
                .filter(Files::exists);
    }
}
