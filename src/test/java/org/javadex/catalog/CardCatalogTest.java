package org.javadex.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesAllCardsFromJson() throws Exception {
        CardCatalog catalog = cardCatalog();
        List<Card> cards = catalog.all();
        assertEquals(2, cards.size());
        assertEquals(new Card("jd01", 1, "Duke", "Hero"), cards.get(0));
    }

    @Test
    void findsCardById() throws Exception {
        CardCatalog catalog = cardCatalog();
        assertEquals("Duke", catalog.byId("jd01").orElseThrow().title());
    }

    @Test
    void unknownIdIsEmpty() throws Exception {
        CardCatalog catalog = cardCatalog();
        assertTrue(catalog.byId("jd99").isEmpty());
    }

    private CardCatalog cardCatalog() throws Exception {
        return new CardCatalog(new ObjectMapper(), writeCardsJson().resolve("cards").toString());
    }

    private Path writeCardsJson() throws Exception {
        Path cardsDir = tempDir.resolve("cards");
        Files.createDirectories(cardsDir);
        Files.writeString(cardsDir.resolve("cards.json"), """
                {
                  "deck": { "name": "Java-Dex", "version": "v0.1" },
                  "cards": [
                    { "id": "jd01", "n": 1, "type": "Hero", "title": "Duke", "role": "Chief Executive Mascot" },
                    { "id": "jd02", "n": 2, "type": "Hero", "title": "JobRunr", "role": "Senior Task Wrangler" }
                  ]
                }
                """);
        return tempDir;
    }
}
