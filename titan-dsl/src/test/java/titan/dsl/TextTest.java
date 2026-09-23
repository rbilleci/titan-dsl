package titan.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void base64UrlOperationsUseUtf8AndAnExactAlphabet() {
        String source = "Grüße 🦀";
        String encoded = Text.base64UrlEncodeUtf8(source);

        assertEquals(source, Text.base64UrlDecodeUtf8(encoded));
        assertEquals(0, Text.base64UrlAlphabetIndex('A'));
        assertEquals(25, Text.base64UrlAlphabetIndex('Z'));
        assertEquals(26, Text.base64UrlAlphabetIndex('a'));
        assertEquals(51, Text.base64UrlAlphabetIndex('z'));
        assertEquals(52, Text.base64UrlAlphabetIndex('0'));
        assertEquals(62, Text.base64UrlAlphabetIndex('-'));
        assertEquals(63, Text.base64UrlAlphabetIndex('_'));
        assertEquals(-1, Text.base64UrlAlphabetIndex('*'));
    }
}
