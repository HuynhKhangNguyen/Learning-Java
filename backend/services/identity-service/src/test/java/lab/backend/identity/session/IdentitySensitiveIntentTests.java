package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Independent Python SHA/HMAC vectors, không gọi implementation để tính expected.
 * Unit binding không chứng minh proof mint/consume, epoch hoặc distributed ACK.
 */
class IdentitySensitiveIntentTests {
  private static final UUID CONTEXT=UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID KEY=UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final String PASSWORD="different new password 🔐";
  private static byte[] secret(){return HexFormat.of().parseHex("01".repeat(32));}

  @Test void independentCanonicalVectorsBindExactUtf8Command() {
    assertThat(IdentitySensitiveIntent.digest("LOGOUT_ALL",CONTEXT,KEY,null))
        .isEqualTo("0165b7c738a219642565b2787c2eeb29d2614f3e941a58ca235047611c0cac81");
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,PASSWORD))
        .isEqualTo("9362d70a037c116f6b923660d82f9925d28375d5de234bf0c1c5076f45feb0db");
  }
  @Test void independentStorageVectorsDoNotPersistPasswordDerivedSha() {
    String hash=IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,PASSWORD);
    byte[] protectedHash=IdentitySensitiveIntent.protectedDigest(secret(),hash);
    assertThat(HexFormat.of().formatHex(protectedHash))
        .isEqualTo("d914949d575c6908930e4c98cd9d7690e3d8084ff04c6e6b15d6baf65d638df1").isNotEqualTo(hash);
    assertThat(HexFormat.of().formatHex(IdentitySensitiveIntent.protectedDigest(secret(),IdentitySensitiveIntent.digest("LOGOUT_ALL",CONTEXT,KEY,null))))
        .isEqualTo("e8098c22d8bc8456001289190d5b5e44723fc715fff735ffc7454d19ded0fbe8");
  }
  @Test void contextKeyAndPasswordChangesCannotReuseIntent() {
    String original=IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,PASSWORD);
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",KEY,KEY,PASSWORD)).isNotEqualTo(original);
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,CONTEXT,PASSWORD)).isNotEqualTo(original);
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,PASSWORD+" ")).isNotEqualTo(original);
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY," "+PASSWORD)).isNotEqualTo(original);
    // Unicode normalization không được đổi password gốc dù nhìn cùng ký tự.
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,"long password é literal"))
        .isNotEqualTo(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,"long password e\u0301 literal"));
  }
  @Test void newlinePayloadUsesLengthFramingNotFieldSeparators() {
    assertThat(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,"new password\nwith newline"))
        .isNotEqualTo(IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,"new passwordwith newline"));
  }
  @Test void unknownActionMissingBindingAndHiddenPasswordAreRejected() {
    for(String action:new String[]{"LEARNING_RESET","LOGOUT","password_change",""})
      assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest(action,CONTEXT,KEY,PASSWORD));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest(null,CONTEXT,KEY,PASSWORD));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest("LOGOUT_ALL",null,KEY,null));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest("LOGOUT_ALL",CONTEXT,null,null));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest("LOGOUT_ALL",CONTEXT,KEY,""));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,"short"));
    assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.digest("PASSWORD_CHANGE",CONTEXT,KEY,PASSWORD+"\ud800"));
  }
  @Test void secretDomainKeyRemainsStableAndDifferentKeysDenyRetarget() {
    byte[] key=secret(),copy=key.clone();String hash=IdentitySensitiveIntent.digest("LOGOUT_ALL",CONTEXT,KEY,null);
    byte[] first=IdentitySensitiveIntent.protectedDigest(key,hash);
    assertThat(key).containsExactly(copy);
    assertThat(IdentitySensitiveIntent.protectedDigest(key,hash)).containsExactly(first);
    key[0]=2;assertThat(IdentitySensitiveIntent.protectedDigest(key,hash)).isNotEqualTo(first);
  }
  @Test void invalidKeyAndNonCanonicalDigestAreRejectedWithoutSecretEcho() {
    for(byte[] key:new byte[][]{null,new byte[31],new byte[32],new byte[33]})
      assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.protectedDigest(key,"a".repeat(64)));
    for(String hash:new String[]{null,"A".repeat(64),"a".repeat(63),"g".repeat(64)})
      assertThatIllegalArgumentException().isThrownBy(()->IdentitySensitiveIntent.protectedDigest(secret(),hash));
  }
}
