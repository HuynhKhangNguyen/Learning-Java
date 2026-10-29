package lab.backend.identity.credential;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("SEC-12") @Tag("SEC-22") @Timeout(90)
class CredentialPackagedIT {
  @TempDir Path emptyDirectory;
  record Output(int exit,String out,String err) {
    @Override public String toString(){return "CliOutput[exit="+exit+",REDACTED]";}
  }
  @BeforeAll static void setup() throws Exception {IdentityCredentialWorkflowIT.setup();}
  @Test void packagedInviteActivateAndVerifyUseOnlyStdinSecrets() throws Exception {
    String name=IdentityCredentialWorkflowIT.name();Output invite=cli(Map.of(),"", "invite",name);
    assertThat(invite.exit()).isZero();String token=token(invite.out());
    var activation=cli(Map.of(),token+"\n"+IdentityCredentialWorkflowIT.PASSWORD+"\n","activate","stdin");
    assertThat(activation.exit()).isZero();assertThat(activation.out().trim()).isEqualTo("VERIFIED");
    assertThat(cli(Map.of(),IdentityCredentialWorkflowIT.PASSWORD+"\n","verify",name).out().trim()).isEqualTo("VERIFIED");
    assertThat(activation.out().contains(token)||activation.err().contains(token)).isFalse();
  }
  @Test void missingCorpusAndWrongHashFailBeforeAnyDatabaseWrite() throws Exception {
    String name=IdentityCredentialWorkflowIT.name();
    assertThat(cli(Map.of("IDENTITY_CORPUS_PATH",emptyDirectory.resolve("absent.txt").toString()),"","invite",name).exit()).isNotZero();
    assertThat(cli(Map.of("IDENTITY_CORPUS_SHA256","00".repeat(32)),"","invite",name).exit()).isNotZero();
    assertThat(IdentityCredentialWorkflowIT.value("SELECT count(*) FROM identity.app_user WHERE login_name='"+name+"'")).isEqualTo("0");
  }
  @Test void wrongRoleAndMissingHmacConfigFailWithoutSecretLog() throws Exception {
    String sentinel="Secret sentinel never log";
    var missing=cli(Map.of("IDENTITY_THROTTLE_KEY",""),sentinel,"verify",IdentityCredentialWorkflowIT.name());
    assertThat(missing.exit()).isNotZero();assertThat(missing.err().contains(sentinel)).isFalse();
    var wrong=cli(Map.of("IDENTITY_DB_USER",IdentityCredentialWorkflowIT.CONFIG.getProperty("identity.migrator.user"),"IDENTITY_DB_PASSWORD",IdentityCredentialWorkflowIT.CONFIG.getProperty("identity.migrator.password")),"","invite",IdentityCredentialWorkflowIT.name());
    assertThat(wrong.exit()).isNotZero();assertThat(wrong.err().trim()).endsWith("MAINTENANCE_FAILED");
    assertThat(wrong.err().contains(IdentityCredentialWorkflowIT.CONFIG.getProperty("identity.migrator.password"))).isFalse();
  }
  @Test void genericDenialAndOversizedInputDoNotEchoSecrets() throws Exception {
    String secret="Never echo this password";
    var output=cli(Map.of(),secret+"\n","verify",IdentityCredentialWorkflowIT.name());
    assertThat(output.out().trim()).isEqualTo("INVALID_CREDENTIALS");assertThat(output.err().contains(secret)).isFalse();
    var tooLarge=cli(Map.of(),"x".repeat(2050),"verify",IdentityCredentialWorkflowIT.name());assertThat(tooLarge.exit()).isNotZero();
    assertThat(tooLarge.err().contains("xxxxx")).isFalse();
  }
  private Output cli(Map<String,String> overrides,String input,String... args) throws Exception {
    Path jar=Path.of("target/identity-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
    assertThat(Files.isRegularFile(jar)).isTrue();
    var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend.identity.credential.IdentityMaintenanceCli","-cp",jar.toString(),"org.springframework.boot.loader.launch.PropertiesLauncher"));
    command.addAll(List.of(args));var builder=new ProcessBuilder(command).directory(emptyDirectory.toFile());
    var env=builder.environment();env.keySet().removeIf(k->k.startsWith("IDENTITY_"));
    env.put("IDENTITY_DB_URL","jdbc:postgresql://127.0.0.1:"+IdentityCredentialWorkflowIT.CONFIG.getProperty("lab.port")+"/identity_db?sslmode=disable");
    env.put("IDENTITY_DB_USER",IdentityCredentialWorkflowIT.CONFIG.getProperty("identity.app.user"));env.put("IDENTITY_DB_PASSWORD",IdentityCredentialWorkflowIT.CONFIG.getProperty("identity.app.password"));
    env.put("IDENTITY_CORPUS_PATH",CredentialPolicyTests.corpusPath().toString());env.put("IDENTITY_CORPUS_SHA256",CredentialPolicyTests.HASH);
    env.put("IDENTITY_THROTTLE_KEY",Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));env.putAll(overrides);
    Process p=builder.start();
    try {
      try(var stdin=p.getOutputStream()){stdin.write(input.getBytes(StandardCharsets.UTF_8));}
      assertThat(p.waitFor(30,TimeUnit.SECONDS)).isTrue();
      return new Output(p.exitValue(),new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8),new String(p.getErrorStream().readAllBytes(),StandardCharsets.UTF_8));
    } finally {if(p.isAlive()){p.destroyForcibly();assertThat(p.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  }
  private static String token(String output) {return output.lines().filter(s->s.startsWith("token=")).findFirst().orElseThrow().substring(6);}
}
