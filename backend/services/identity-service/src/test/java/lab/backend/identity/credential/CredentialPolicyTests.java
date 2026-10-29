package lab.backend.identity.credential;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CredentialPolicyTests {
  static final String HASH="3fea5097f7cc4eeea576bcbbb2fdbf010967555ebcff33da93a18879ef71b84c";
  @Test void loginNormalizationIsAsciiAndDeterministic(){assertThat(CredentialWorkflow.normalizeLoginName("  User.Name-1 ")).isEqualTo("user.name-1");assertThatThrownBy(()->CredentialWorkflow.normalizeLoginName("usér")).isInstanceOf(IllegalArgumentException.class);}
  @Test void unicodePolicyCountsCodePointsWithoutTrimmingOrSeventyTwoByteTruncation(){String p="  🔐密碼 café "+"x".repeat(20);PasswordPolicy.requireValid(p);var h=new Argon2CredentialHasher();String encoded=h.hash(p);assertThat(h.matches(p,encoded)).isTrue();assertThat(h.matches(p.strip(),encoded)).isFalse();String longBytes="密".repeat(30);assertThat(longBytes.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeGreaterThan(72);assertThat(h.matches(longBytes,h.hash(longBytes))).isTrue();}
  static Path corpusPath(){Path p=Path.of("").toAbsolutePath();for(int i=0;i<6&&p!=null;i++,p=p.getParent()){Path candidate=p.resolve("ops/identity/password-corpus.txt");if(java.nio.file.Files.isRegularFile(candidate))return candidate;}throw new IllegalStateException("Password corpus not found below workspace ancestry");}
  @Test void corpusIsPinnedAndFailsClosed() throws Exception {var corpus=PasswordCorpus.load(corpusPath(),HASH);assertThatThrownBy(()->corpus.requireAllowed("passwordpassword")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->PasswordCorpus.load(corpusPath(),"00".repeat(32))).isInstanceOf(IllegalStateException.class);}
  @Test void boundariesAreCodePoints(){PasswordPolicy.requireValid("🔐".repeat(15));PasswordPolicy.requireValid("x".repeat(128));assertThatThrownBy(()->PasswordPolicy.requireValid("x".repeat(14))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->PasswordPolicy.requireValid("x".repeat(129))).isInstanceOf(IllegalArgumentException.class);}
  @Test void encodedFormatContainsBaselineAndUsesFreshSalt(){var h=new Argon2CredentialHasher();String a=h.hash("valid password value");String b=h.hash("valid password value");assertThat(a).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");assertThat(a).isNotEqualTo(b);String[] fields=a.split("\\$");assertThat(java.util.Base64.getDecoder().decode(fields[4])).hasSize(16);assertThat(java.util.Base64.getDecoder().decode(fields[5])).hasSize(32);}
  @Test void unicodeIsNotNormalizedAndUnpairedSurrogateIsRejected(){var h=new Argon2CredentialHasher();String p="caf\u00e9 password value";assertThat(h.matches("cafe\u0301 password value",h.hash(p))).isFalse();assertThatThrownBy(()->PasswordPolicy.requireValid("x".repeat(15)+'\ud800')).isInstanceOf(IllegalArgumentException.class);}
  @Test void equivalentIpv6SourcesUseOneBucketAndHostnamesAreRejected(){assertThat(CredentialVerifier.canonicalIp("::1")).isEqualTo(CredentialVerifier.canonicalIp("0:0:0:0:0:0:0:1"));assertThatThrownBy(()->CredentialVerifier.canonicalIp("forwarded.example")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->CredentialVerifier.canonicalIp("999.9.9.9")).isInstanceOf(IllegalArgumentException.class);}
  @Test void unknownWrongAndNonActiveAccountsHaveTheSameExternalDenial(){var h=new Argon2CredentialHasher();var v=new CredentialVerifier(h);String stored=h.hash("correct password value");var unknown=v.verify("wrong password value",null,null);assertThat(v.verify("wrong password value",stored,"ACTIVE")).isEqualTo(unknown);assertThat(v.verify("correct password value",stored,"DISABLED")).isEqualTo(unknown);assertThat(v.verify("correct password value",stored,"INVITED")).isEqualTo(unknown);assertThat(v.verify("correct password value",stored,"RECOVERY_REQUIRED")).isEqualTo(unknown);}

  /** Policy/corpus deny trước expensive hash; Prepared không lộ hash khi log. */
  @Test void sensitivePreparationChecksPolicyAndCorpusBeforeKdf()throws Exception {
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    var verifier=new CredentialVerifier(new PasswordHasher(){
      public String hash(String input){calls.incrementAndGet();return "private-hash-fixture";}
      public boolean matches(String input,String encoded){return false;}
    });
    var corpus=PasswordCorpus.load(corpusPath(),HASH);
    assertThat(verifier.preparePassword("short",corpus).result().code()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(verifier.preparePassword("passwordpassword",corpus).result().code()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(calls.get()).isZero();
    var result=verifier.preparePassword("valid new password value",corpus);
    assertThat(result.result().accepted()).isTrue();assertThat(calls.get()).isEqualTo(1);
    assertThat(result.toString()).doesNotContain(result.hash(),"valid new password value");
  }

  /** Hai KDF giữ slots cùng singleton; thứ ba bị từ chối ngay, không queue vô
   * hạn. Kết thúc/throw phải release slots để request sau không bị kẹt. */
  @Test void sensitivePreparationIsBoundedAndReleasesSingletonSlots()throws Exception {
    var entered=new java.util.concurrent.CountDownLatch(2);var resume=new java.util.concurrent.CountDownLatch(1);
    var verifier=new CredentialVerifier(new PasswordHasher(){
      public String hash(String input){
        entered.countDown();
        try{if(!resume.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("fixture interrupted");}
        return "private-hash-fixture";
      }
      public boolean matches(String input,String encoded){return false;}
    });
    var corpus=PasswordCorpus.load(corpusPath(),HASH);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      var one=pool.submit(()->verifier.preparePassword("valid new password one",corpus));
      var two=pool.submit(()->verifier.preparePassword("valid new password two",corpus));
      assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(verifier.preparePassword("valid new password three",corpus).result().code()).isEqualTo("THROTTLED");
      resume.countDown();assertThat(one.get(5,java.util.concurrent.TimeUnit.SECONDS).result().accepted()).isTrue();
      assertThat(two.get(5,java.util.concurrent.TimeUnit.SECONDS).result().accepted()).isTrue();
      assertThat(verifier.preparePassword("valid new password four",corpus).result().accepted()).isTrue();
    }finally{resume.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
  }

  @Test void sensitivePreparationKdfFailureDoesNotLeakOrKeepSlot()throws Exception {
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    var verifier=new CredentialVerifier(new PasswordHasher(){
      public String hash(String input){if(calls.incrementAndGet()<=2)throw new IllegalStateException("private-secret-fixture");return "private-hash-fixture";}
      public boolean matches(String input,String encoded){return false;}
    });
    var corpus=PasswordCorpus.load(corpusPath(),HASH);
    for(int i=0;i<2;i++){
      var result=verifier.preparePassword("valid new password value",corpus);
      assertThat(result.hash()).isNull();assertThat(result.result().code()).isEqualTo("UNAVAILABLE");
      assertThat(result.toString()+result.result()).doesNotContain("private-secret-fixture");
    }
    assertThat(verifier.preparePassword("valid new password value",corpus).result().accepted()).isTrue();
  }
}
