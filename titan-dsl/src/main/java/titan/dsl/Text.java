package titan.dsl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Portable text operations with explicitly defined UTF-8 semantics.
 *
 * <p>The Titan transpiler recognizes these methods as database intrinsics. Their Java bodies keep
 * direct JVM execution useful for tests and tooling; they are not invoked by a transpiled routine.
 * The URL-safe Base64 representation is unpadded, matching {@link Base64#getUrlEncoder()} followed
 * by {@link Base64.Encoder#withoutPadding()}.</p>
 */
public final class Text {

    private static final String BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private Text() {
    }

    /** Returns the unpadded URL-safe Base64 encoding of this value's UTF-8 bytes. */
    public static String base64UrlEncodeUtf8(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an unpadded URL-safe Base64 value as UTF-8 text. */
    public static String base64UrlDecodeUtf8(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /**
     * Returns this character's case-exact position in the URL-safe Base64 alphabet, or {@code -1}.
     *
     * <p>This is intentionally narrower than {@link String#indexOf(int)}: it gives transpiled
     * validation code a collation-independent ASCII lookup whose database implementation can
     * preserve Java's case-sensitive character semantics.</p>
     */
    public static int base64UrlAlphabetIndex(char value) {
        return BASE64_URL_ALPHABET.indexOf(value);
    }
}
