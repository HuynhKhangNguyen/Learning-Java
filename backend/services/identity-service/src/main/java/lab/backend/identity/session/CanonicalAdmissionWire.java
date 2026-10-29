package lab.backend.identity.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;
import tools.jackson.databind.JsonNode;

/**
 * Kiểm request INT01 tại Identity trước khi mở transaction canonical.
 * Caller phải đến từ certificate mTLS đã xác minh, không từ field/header browser.
 * Lớp này chỉ kiểm hình dạng và binding; chưa xác thực session, cấp principal,
 * kiểm CSRF synchronizer hay trả ALLOW. Các bước đó thuộc canonical authority.
 */
public final class CanonicalAdmissionWire {
  public static final int MAX_BYTES = 8192;
  public static final String PATH = "/internal/v1/auth-context/admissions";
  private static final Set<String> READ_FIELDS = Set.of(
      "schemaVersion", "action", "audience", "requestNonce", "sessionCredential",
      "expectedContextId", "method", "canonicalPath", "query", "bodySha256", "requestBinding");
  private static final Set<String> MUTATION_FIELDS;
  private static final String EMPTY_SHA = sha(new byte[0]);
  static {
    var fields = new HashSet<>(READ_FIELDS);
    fields.add("csrfToken"); fields.add("origin");
    MUTATION_FIELDS = Set.copyOf(fields);
  }

  /** Metadata giữ trong một request; không log credential/CSRF và không cache ALLOW. */
  public record Request(String caller, String action, UUID nonce, String credential,
      UUID context, String method, String canonicalPath, String bodySha256,
      String binding, String csrfToken, boolean mutation) {
    @Override public String toString() { return "CanonicalAdmissionRequest[credentials=REDACTED]"; }
  }

  /**
   * Nhận exact body, verified caller và Origin từ cấu hình runtime đã kiểm.
   * Từ chối unknown keys, action chưa đăng ký và route mơ hồ trước DB/KDF.
   * CSRF ở đây chỉ là metadata đúng format; authority phải so với token của session.
   */
  public static Request parse(byte[] bytes, String verifiedCaller, String configuredOrigin) {
    if (!Set.of("content", "learning").contains(verifiedCaller == null ? "" : verifiedCaller))
      throw new Denied(403, "ADMISSION_CALLER_DENIED");
    if (bytes == null) throw new Denied(400, "BAD_WIRE");
    if (bytes.length > MAX_BYTES) throw new Denied(413, "PAYLOAD_TOO_LARGE");
    JsonNode node = WireCodec.strict(bytes);
    boolean mutation = verifiedCaller.equals("learning");
    var fields = new HashSet<String>();
    node.properties().forEach(p -> fields.add(p.getKey()));
    if (!fields.equals(mutation ? MUTATION_FIELDS : READ_FIELDS)
        || !node.get("schemaVersion").isInt() || node.get("schemaVersion").asInt() != 1)
      throw new Denied(400, "UNSUPPORTED_SCHEMA");

    String action = text(node, "action"), audience = text(node, "audience");
    String allowedAction = mutation ? "LEARNING_DRAFT_SAVE" : "CONTENT_BODY_READ";
    if (!audience.equals(verifiedCaller) || !action.equals(allowedAction))
      throw new Denied(403, "ADMISSION_BINDING_DENIED");
    UUID nonce = id(node, "requestNonce"), context = id(node, "expectedContextId");
    String credential = text(node, "sessionCredential");
    if (!credential.matches("[A-Za-z0-9_-]{43}")) throw new Denied(401, "SESSION_INVALID");
    String method = text(node, "method"), path = text(node, "canonicalPath");
    String query = text(node, "query"), bodySha = hex(node, "bodySha256");
    if (!query.isEmpty()) throw new Denied(403, "ADMISSION_BINDING_DENIED");

    // Policy hiện chỉ có hai shape mà receiver đã triển khai. Không coi service
    // certificate là quyền gọi mọi learner action; task endpoint sau phải đăng ký rõ.
    String csrf = null;
    if (mutation) {
      if (!method.equals("PUT") || !path.startsWith("/api/v1/drafts/"))
        throw new Denied(403, "ADMISSION_BINDING_DENIED");
      WireCodec.idempotencyKey(path.substring("/api/v1/drafts/".length()));
      csrf = text(node, "csrfToken");
      if (!csrf.matches("[A-Za-z0-9_-]{43}")) throw new Denied(403, "CSRF_DENIED");
      if (configuredOrigin == null || !configuredOrigin.equals(text(node, "origin")))
        throw new Denied(403, "ORIGIN_DENIED");
    } else if (!method.equals("GET") || !bodySha.equals(EMPTY_SHA)
        || !path.matches("/api/v1/topics/[A-Za-z0-9][A-Za-z0-9_-]{0,95}/units/[A-Za-z0-9][A-Za-z0-9_-]{0,95}")) {
      throw new Denied(403, "ADMISSION_BINDING_DENIED");
    }

    // Tự tính lại binding thay vì tin digest caller gửi. Nó ràng method, route,
    // query đã allowlist và hash exact validated body ở receiver, không phải owner.
    String binding = hex(node, "requestBinding");
    if (!binding.equals(sha((method + "\n" + path + "\n\n" + bodySha).getBytes(StandardCharsets.UTF_8))))
      throw new Denied(403, "ADMISSION_BINDING_DENIED");
    return new Request(verifiedCaller, action, nonce, credential, context, method,
        path, bodySha, binding, csrf, mutation);
  }

  private static String text(JsonNode node, String key) { return WireCodec.string(node, key); }
  private static UUID id(JsonNode node, String key) {
    return UUID.fromString(WireCodec.idempotencyKey(text(node, key)));
  }
  private static String hex(JsonNode node, String key) {
    String value = text(node, key);
    if (!value.matches("[0-9a-f]{64}")) throw new Denied(400, "BAD_WIRE");
    return value;
  }
  private static String sha(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA256 unavailable"); }
  }
  private CanonicalAdmissionWire() {}
}
