package lab.backend.identity.session;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;
import tools.jackson.databind.JsonNode;

/** INT-06 chỉ chuyển proof/binding tới own authority; parser không cấp quyền
 * từ userId do caller gửi. Authority phải so từng field với grant canonical. */
public final class ReauthReservationWire {
  public static final String PATH="/internal/v1/reauth-authorizations/reservations";
  public static final int MAX_BYTES=8192;
  private static final Set<String> FIELDS=Set.of("schemaVersion","proof","userId","sessionRef",
      "sessionContextId","credentialEpoch","authRealmId","purpose","operationKey","intentHash");
  private static final DateTimeFormatter TIME=new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

  /** Reject caller trước body; password, action hoặc field phụ không đi vào DB.
   * Epoch là decimal string để không mất precision khi receiver dùng JavaScript. */
  public static ReauthAuthority.Request parse(byte[] bytes,String verifiedCaller) {
    if(!"learning".equals(verifiedCaller))throw new Denied(403,"REAUTH_CALLER_DENIED");
    if(bytes==null)throw new Denied(400,"BAD_WIRE");
    if(bytes.length>MAX_BYTES)throw new Denied(413,"PAYLOAD_TOO_LARGE");
    JsonNode n=WireCodec.strict(bytes);var names=new HashSet<String>();n.properties().forEach(p->names.add(p.getKey()));
    if(!names.equals(FIELDS)||!n.get("schemaVersion").isInt()||n.get("schemaVersion").asInt()!=1)throw new Denied(400,"UNSUPPORTED_SCHEMA");
    String proof=WireCodec.string(n,"proof"),purpose=WireCodec.string(n,"purpose"),intent=WireCodec.string(n,"intentHash");
    if(SessionAuthority.digest(proof)==null)throw new Denied(401,"REAUTH_PROOF_DENIED");
    long epoch=Long.parseLong(WireCodec.decimal(n.get("credentialEpoch")));
    if(epoch<1||!ReauthAuthority.PURPOSE.equals(purpose)||!intent.matches("[0-9a-f]{64}"))throw new Denied(400,"BAD_REAUTH_BINDING");
    return new ReauthAuthority.Request(proof,id(n,"userId"),id(n,"sessionRef"),id(n,"sessionContextId"),
        epoch,id(n,"authRealmId"),purpose,id(n,"operationKey"),intent);
  }

  /** Response không raw proof/password; reference chỉ là reservation, chưa reset
   * commit. Floor TTL tới millisecond không nới deadline DB đã lưu. */
  public static Map<String,Object> json(ReauthAuthority.Reservation r) {
    var out=new LinkedHashMap<String,Object>();out.put("schemaVersion",1);out.put("state","RESERVED");
    out.put("provider","identity");out.put("caller","learning");out.put("audience","learning");
    out.put("reservationRef",r.reference().toString());out.put("grantRef",r.grantRef().toString());
    out.put("userId",r.user().toString());out.put("sessionRef",r.session().toString());
    out.put("sessionContextId",r.context().toString());out.put("credentialEpoch",Long.toString(r.epoch()));
    out.put("authRealmId",r.realm().toString());out.put("purpose",r.purpose());
    out.put("operationKey",r.operationKey().toString());out.put("intentHash",r.intentHash());
    out.put("expiresAt",TIME.format(r.expiresAt()));return Map.copyOf(out);
  }
  private static UUID id(JsonNode n,String key){return UUID.fromString(WireCodec.idempotencyKey(WireCodec.string(n,key)));}
  private ReauthReservationWire(){}
}
