package be.bstorm.formation.support;

import com.jayway.jsonpath.JsonPath;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Lecture et fabrication de JWT compacts pour les tests (JDK seul, aucune bibliothèque JOSE). */
public final class JwtTestTokens {

    private JwtTestTokens() {
    }

    public static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String b64url(String json) {
        return b64url(json.getBytes(StandardCharsets.UTF_8));
    }

    public static Map<String, Object> header(String token) {
        return parse(segment(token, 0));
    }

    public static Map<String, Object> payload(String token) {
        return parse(segment(token, 1));
    }

    public static byte[] signature(String token) {
        return Base64.getUrlDecoder().decode(token.split("\\.", -1)[2]);
    }

    /** {@code <header b64url>.<payload b64url>} */
    public static String signingInput(String token) {
        return token.substring(0, token.lastIndexOf('.'));
    }

    /** JSON minimal : chaînes, nombres, booléens, collections. */
    public static String toJson(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(e -> quote(e.getKey()) + ":" + value(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Collection<?> c) {
            return c.stream().map(JwtTestTokens::value).collect(Collectors.joining(",", "[", "]"));
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String segment(String token, int index) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.", -1)[index]), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parse(String json) {
        return JsonPath.parse(json).read("$");
    }
}
