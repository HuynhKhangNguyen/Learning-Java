package lab.backend.identity.session;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lab.backend.identity.transport.WireCodec;
import org.junit.jupiter.api.Test;

/** Registered shapes không cấp authority; session/CSRF thực thuộc integration suite. */
class AllocationAdmissionWireTests {
  private static final String ORIGIN = "https://127.0.0.1";
  private static final String ATTEMPT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

  @Test
  void createRequiresMutationFieldsAndReadUsesReadFieldsWithoutCsrf() {
    var create = request(true);
    assertTrue(parse(create).mutation());
    var read = request(false);
    var parsed = parse(read);
    assertFalse(parsed.mutation());
    assertNull(parsed.csrfToken());
    assertEquals("LEARNING_ATTEMPT_READ", parsed.action());
    create.remove("csrfToken");
    assertThrows(WireCodec.Denied.class, () -> parse(create));
    read.put("csrfToken", "D".repeat(43));
    read.put("origin", ORIGIN);
    assertThrows(WireCodec.Denied.class, () -> parse(read));
  }

  @Test
  void rehashedCrossActionMethodAudiencePathBodyAndQueryCannotGainAdmission() {
    for (String kind : new String[]{"action", "method", "audience", "path", "encoded",
        "body", "query", "receipt", "origin", "csrf"}) {
      var value = request(false);
      switch (kind) {
        case "action" -> value.put("action", "LEARNING_ATTEMPT_CREATE");
        case "method" -> value.put("method", "POST");
        case "audience" -> value.put("audience", "content");
        case "path" -> value.put("canonicalPath", "/api/v1/attempts/" + ATTEMPT + "/");
        case "encoded" -> value.put("canonicalPath", "/api/v1/attempts/%61" + ATTEMPT.substring(1));
        case "body" -> value.put("bodySha256", "0".repeat(64));
        case "query" -> value.put("query", "owner=forged");
        case "receipt" -> {
          value.put("action", "LEARNING_ALLOCATION_RECEIPT_READ");
          value.put("canonicalPath", "/api/v1/receipts/allocation/" + ATTEMPT);
        }
        case "origin" -> {
          value = request(true);
          value.put("origin", "https://untrusted.invalid");
        }
        case "csrf" -> {
          value = request(true);
          value.put("csrfToken", "invalid");
        }
        default -> fail("Unknown fixture");
      }
      bind(value);
      final var denied = value;
      assertThrows(WireCodec.Denied.class, () -> parse(denied), kind);
    }
  }

  private static CanonicalAdmissionWire.Request parse(Map<String, Object> value) {
    return CanonicalAdmissionWire.parse(WireCodec.JSON.writeValueAsBytes(value), "learning", ORIGIN);
  }

  private static Map<String, Object> request(boolean mutation) {
    var value = new LinkedHashMap<String, Object>();
    value.put("schemaVersion", 1);
    value.put("action", mutation ? "LEARNING_ATTEMPT_CREATE" : "LEARNING_ATTEMPT_READ");
    value.put("audience", "learning");
    value.put("requestNonce", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    value.put("sessionCredential", "C".repeat(43));
    value.put("expectedContextId", "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    value.put("method", mutation ? "POST" : "GET");
    value.put("canonicalPath", mutation ? "/api/v1/attempts" : "/api/v1/attempts/" + ATTEMPT);
    value.put("query", "");
    value.put("bodySha256", sha(mutation ? "{}" : ""));
    if (mutation) {
      value.put("csrfToken", "D".repeat(43));
      value.put("origin", ORIGIN);
    }
    bind(value);
    return value;
  }

  private static void bind(Map<String, Object> value) {
    value.put("requestBinding", sha(value.get("method") + "\n" + value.get("canonicalPath")
        + "\n" + value.get("query") + "\n" + value.get("bodySha256")));
  }

  private static String sha(String text) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception unavailable) {
      throw new AssertionError(unavailable);
    }
  }
}
