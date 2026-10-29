package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parse/response vectors độc lập; không thay actual proof DB/caller TLS test. */
class ReauthReservationWireTests {
  static final UUID USER=UUID.randomUUID(),SESSION=UUID.randomUUID(),CONTEXT=UUID.randomUUID(),REALM=UUID.randomUUID(),KEY=UUID.randomUUID();
  static final String PROOF="a".repeat(43),INTENT="b".repeat(64);
  static Map<String,Object> body(){var b=new LinkedHashMap<String,Object>();b.put("schemaVersion",1);b.put("proof",PROOF);b.put("userId",USER.toString());b.put("sessionRef",SESSION.toString());b.put("sessionContextId",CONTEXT.toString());b.put("credentialEpoch","9007199254740993");b.put("authRealmId",REALM.toString());b.put("purpose","LEARNING_RESET");b.put("operationKey",KEY.toString());b.put("intentHash",INTENT);return b;}
  static byte[] bytes(Map<String,Object> body){return WireCodec.JSON.writeValueAsBytes(body);}
  static void deny(byte[] body,int status){assertThatThrownBy(()->ReauthReservationWire.parse(body,"learning")).isInstanceOfSatisfying(Denied.class,d->assertThat(d.status).isEqualTo(status));}

  @Test void exactBindingAndBigEpochSurviveWithoutLoggingProof(){
    var r=ReauthReservationWire.parse(bytes(body()),"learning");
    assertThat(r.user()).isEqualTo(USER);assertThat(r.session()).isEqualTo(SESSION);assertThat(r.context()).isEqualTo(CONTEXT);assertThat(r.realm()).isEqualTo(REALM);assertThat(r.operationKey()).isEqualTo(KEY);
    assertThat(r.epoch()).isEqualTo(9007199254740993L);assertThat(r.purpose()).isEqualTo("LEARNING_RESET");assertThat(r.intentHash()).isEqualTo(INTENT);assertThat(r.proof()).isEqualTo(PROOF);
    assertThat(r.toString()).doesNotContain(PROOF,INTENT,USER.toString());
  }
  @ParameterizedTest @ValueSource(strings={"identity","content","edge","browser",""})
  void callerRejectedBeforeMalformedBody(String caller){assertThatThrownBy(()->ReauthReservationWire.parse(null,caller)).isInstanceOfSatisfying(Denied.class,d->assertThat(d.status).isEqualTo(403));}
  @ParameterizedTest @ValueSource(strings={"schemaVersion","proof","userId","sessionRef","sessionContextId","credentialEpoch","authRealmId","purpose","operationKey","intentHash"})
  void everyFieldIsRequired(String field){var b=body();b.remove(field);deny(bytes(b),400);}
  @ParameterizedTest @ValueSource(strings={"userId","sessionRef","sessionContextId","authRealmId","operationKey"})
  void uuidMustBeCanonical(String field){for(String value:List.of("1-1-1-1-1",USER.toString().toUpperCase(Locale.ROOT),"not-uuid")){var b=body();b.put(field,value);deny(bytes(b),400);}}
  @ParameterizedTest @ValueSource(strings={"0","01","-1","9223372036854775808","1.0"," 1",""})
  void epochMustBePositiveLongDecimalString(String epoch){var b=body();b.put("credentialEpoch",epoch);deny(bytes(b),400);}
  @Test void numberEpochUnknownKeysPurposeAndHashAreDenied(){
    var b=body();b.put("credentialEpoch",1);deny(bytes(b),400);
    b=body();b.put("password","secret-sentinel");deny(bytes(b),400);
    b=body();b.put("purpose","LOGOUT_ALL");deny(bytes(b),400);
    b=body();b.put("intentHash","B".repeat(64));deny(bytes(b),400);
    b=body();b.put("schemaVersion",2);deny(bytes(b),400);
    b=body();b.put("proof","a".repeat(42));deny(bytes(b),401);
  }
  @Test void strictJsonAndByteLimitCannotBeBypassed(){
    for(byte[] b:new byte[][]{new byte[]{(byte)0xc3,(byte)0x28},"{} {}".getBytes(StandardCharsets.UTF_8),"{\"schemaVersion\":1,\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8),"{\"proof\":\"\\ud800\"}".getBytes(StandardCharsets.UTF_8)})deny(b,400);
    deny(new byte[8193],413);deny(null,400);
  }
  @Test void reservedResponseHasFixedBindingNoRawProofAndFlooredTtl(){
    UUID reference=UUID.randomUUID(),grant=UUID.randomUUID();
    var response=ReauthReservationWire.json(new ReauthAuthority.Reservation(reference,grant,USER,SESSION,CONTEXT,9007199254740993L,REALM,"LEARNING_RESET",KEY,INTENT,Instant.parse("2026-10-09T00:00:00.123999Z")));
    assertThat(response.keySet()).containsExactlyInAnyOrder("schemaVersion","state","provider","caller","audience","reservationRef","grantRef","userId","sessionRef","sessionContextId","credentialEpoch","authRealmId","purpose","operationKey","intentHash","expiresAt");
    assertThat(response).containsEntry("state","RESERVED").containsEntry("reservationRef",reference.toString()).containsEntry("grantRef",grant.toString()).containsEntry("credentialEpoch","9007199254740993").containsEntry("expiresAt","2026-10-09T00:00:00.123Z").containsEntry("purpose","LEARNING_RESET").containsEntry("operationKey",KEY.toString()).containsEntry("intentHash",INTENT);
    assertThat(new String(bytes(response),StandardCharsets.UTF_8)).doesNotContain(PROOF,"password","COMPLETED");
  }
}
