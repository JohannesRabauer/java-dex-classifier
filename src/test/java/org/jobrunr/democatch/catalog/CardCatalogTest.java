package org.jobrunr.democatch.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardCatalogTest {

    @TempDir
    Path tempDir;

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

    @Test
    void parsesAllCardsFromJson() throws Exception {
        CardCatalog catalog = new CardCatalog(writeCardsJson().resolve("cards").toString());
        List<Card> cards = catalog.all();
        assertEquals(2, cards.size());
        assertEquals(new Card("jd01", 1, "Duke", "Hero"), cards.get(0));
    }

    @Test
    void findsCardById() throws Exception {
        CardCatalog catalog = new CardCatalog(writeCardsJson().resolve("cards").toString());
        assertEquals("Duke", catalog.byId("jd01").orElseThrow().title());
    }

    @Test
    void unknownIdIsEmpty() throws Exception {
        CardCatalog catalog = new CardCatalog(writeCardsJson().resolve("cards").toString());
        assertTrue(catalog.byId("jd99").isEmpty());
    }
}
