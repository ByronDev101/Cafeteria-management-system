package ke.ac.kca.cafeteria.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TableTokenTest {

    @Test
    void generatedTokensAreLongUniqueAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String token = TableToken.generate();
            assertEquals(TableToken.LENGTH, token.length());
            assertTrue(TableToken.normalize(token).isPresent());
            assertTrue(seen.add(token), "tokens must not repeat");
        }
    }

    @Test
    void displayGroupsInFoursAndCanBeTypedBackInAnyCase() {
        String token = TableToken.generate();
        String shown = TableToken.display(token);
        assertEquals(32, shown.length());
        assertEquals(token, TableToken.normalize(shown).orElseThrow());
        assertEquals(token, TableToken.normalize(shown.toLowerCase()).orElseThrow());
        assertEquals(token, TableToken.normalize("  " + shown.replace('-', ' ') + " ").orElseThrow());
    }

    @Test
    void lookAlikeLettersAreReadTheWayThePrintedCodeMeant() {
        assertEquals("0000" + "1111" + "1111" + "0000" + "1111" + "1111" + "00",
                TableToken.normalize("OOOO" + "IIII" + "LLLL" + "0000" + "1111" + "1111" + "00").orElseThrow());
    }

    @Test
    void malformedInputIsRejected() {
        String token = TableToken.generate();
        assertTrue(TableToken.normalize(token.substring(1)).isEmpty());
        assertTrue(TableToken.normalize(token + "A").isEmpty());
        assertTrue(TableToken.normalize("U".repeat(26)).isEmpty());
        assertTrue(TableToken.normalize("!".repeat(26)).isEmpty());
        assertTrue(TableToken.normalize("' OR 1=1 --").isEmpty());
        assertTrue(TableToken.normalize("").isEmpty());
        assertTrue(TableToken.normalize(null).isEmpty());
    }

    @Test
    void extractAcceptsWholeLinksAndBareCodes() {
        String token = TableToken.generate();
        assertEquals(token, TableToken.extract("https://192.168.1.5:8080/t/" + token).orElseThrow());
        assertEquals(token, TableToken.extract("http://host/t/" + token + "?x=1#top").orElseThrow());
        assertEquals(token, TableToken.extract("http://host/t/" + token + "/").orElseThrow());
        assertEquals(token, TableToken.extract(TableToken.display(token)).orElseThrow());
        assertTrue(TableToken.extract("http://host/t/not-a-token").isEmpty());
        assertTrue(TableToken.extract(null).isEmpty());
    }

    @Test
    void hashIsStable64HexAndDiffersPerToken() {
        String token = TableToken.generate();
        String hash = TableToken.hash(token);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, TableToken.hash(token));
        assertNotEquals(hash, TableToken.hash(TableToken.generate()));
        assertFalse(hash.contains(token));
    }
}
