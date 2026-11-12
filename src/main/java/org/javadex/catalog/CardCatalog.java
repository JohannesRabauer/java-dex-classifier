package org.javadex.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Component
public class CardCatalog {

    private final List<Card> cards;

    public CardCatalog(ObjectMapper objectMapper, @Value("${app.cards-dir:cards}") String cardsDir) {
        Path cardsDirAsPath = Path.of(cardsDir);
        try {
            JsonNode root = objectMapper.readTree(Files.readString(cardsDirAsPath.resolve("cards.json")));
            this.cards = StreamSupport.stream(root.path("cards").spliterator(), false)
                    .map(c -> new Card(
                            c.path("id").asString(),
                            c.path("n").asInt(),
                            c.path("title").asString(),
                            c.path("type").asString()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + cardsDirAsPath.resolve("cards.json"), e);
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
}
