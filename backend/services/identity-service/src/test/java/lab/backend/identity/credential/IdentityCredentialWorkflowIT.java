package lab.backend.identity.credential;

import static org.assertj.core.api.Assertions.*;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;

@Timeout(90)
class IdentityCredentialWorkflowIT {
  static final Properties CONFIG=new Properties();
  static PGSimpleDataSource ds;
  static CredentialWorkflow workflow;
  static PasswordCorpus corpus;
  static final String PASSWORD="  密碼🔐 correct password value  ";
  @BeforeAll static void setup() throws Exception {
    String input=System.getProperty("lab.config");
    if(input==null||input.isBlank()||input.startsWith("${"))throw new IllegalStateException("R03 requires managed PostgreSQL");
    Path path=Path.of(input).toRealPath();
    assertThat(Files.isRegularFile(path.getParent().resolve("ownership.json"))).isTrue();
    try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){CONFIG.load(r);}
    ds=source("app");workflow=new CredentialWorkflow(ds);
    corpus=PasswordCorpus.load(CredentialPolicyTests.corpusPath(),CredentialPolicyTests.HASH);
    try(Connection c=ds.getConnection()) {
      assertThat(scalar(c,"SELECT current_database()||':'||current_user")).isEqualTo("identity_db:identity_app");
      assertThat(scalar(c,"SELECT current_setting('backend_lab.run_id')")).isEqualTo(CONFIG.getProperty("lab.runId"));
    }
  }

  @Test @Tag("SEC-12") void inviteNormalizesAndStoresOnlyDigest() throws Exception {
    String name=name();var issued=workflow.invite(" "+name.toUpperCase(Locale.ROOT)+" ");
    assertThat(issued.rawToken()).hasSize(43);assertThat(issued.toString()).doesNotContain(issued.rawToken());
    try(Connection c=ds.getConnection();var s=c.prepareStatement("SELECT login_name,state,password_hash,octet_length(token_digest),extract(epoch from expires_at-credential_token.created_at) FROM identity.app_user JOIN identity.credential_token USING(user_id) WHERE user_id=?")) {
      s.setObject(1,issued.userId());try(var r=s.executeQuery()){r.next();assertThat(r.getString(1)).isEqualTo(name);assertThat(r.getString(2)).isEqualTo("INVITED");assertThat(r.getString(3)).isNull();assertThat(r.getInt(4)).isEqualTo(32);assertThat(r.getDouble(5)).isBetween(86398d,86402d);}
    }
  }
  @Test @Tag("SEC-12") void sameNormalizedInviteHasOneDatabaseWinner() throws Exception {
    String name=name();var gate=new CyclicBarrier(2);
    List<Boolean> results=two(()->{gate.await();try{workflow.invite(" "+name.toUpperCase(Locale.ROOT)+" ");return true;}catch(SQLException duplicate){assertThat(duplicate.getSQLState()).isEqualTo("23505");return false;}});
    assertThat(results).containsExactlyInAnyOrder(true,false);
    assertThat(value("SELECT count(*) FROM identity.app_user WHERE login_name='"+name+"'")).isEqualTo("1");
  }
  @Test @Tag("SEC-12") void activationTokenRaceHasExactlyOneWinner() throws Exception {
    var token=workflow.invite(name());var gate=new CyclicBarrier(2);
    assertThat(two(()->{gate.await();return workflow.consume(CredentialWorkflow.Purpose.ACTIVATE,token.rawToken(),PASSWORD,corpus);})).containsExactlyInAnyOrder(true,false);
    assertThat(account(token.userId(),"state||':'||credential_epoch")).isEqualTo("ACTIVE:1");
  }
  @Test @Tag("SEC-12") void resetTokenRaceHasExactlyOneWinnerAndDurablePendingIntent() throws Exception {
    var a=active();var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);var gate=new CyclicBarrier(2);
    assertThat(two(()->{gate.await();return workflow.consume(CredentialWorkflow.Purpose.RESET,reset.rawToken(),"replacement password value",corpus);})).containsExactlyInAnyOrder(true,false);
    assertThat(account(a.userId(),"state||':'||credential_epoch||':'||auth_transition_pending")).isEqualTo("ACTIVE:2:true");
    assertThat(value("SELECT count(*) FROM identity.credential_transition WHERE user_id='"+a.userId()+"' AND state='PENDING'")).isEqualTo("1");
    assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+a.userId()+"'")).isEqualTo("0");
  }
  @Test @Tag("SEC-12") void purposeExpiryAndReissueCannotConsumeOldTokens() throws Exception {
    var invited=workflow.invite(name());
    assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,invited.rawToken(),PASSWORD,corpus)).isFalse();
    var replacement=workflow.reissue(invited.userId(),CredentialWorkflow.Purpose.ACTIVATE);
    assertThat(workflow.consume(CredentialWorkflow.Purpose.ACTIVATE,invited.rawToken(),PASSWORD,corpus)).isFalse();
    update("UPDATE identity.credential_token SET created_at=clock_timestamp()-interval '2 hours',expires_at=clock_timestamp()-interval '1 second' WHERE user_id='"+invited.userId()+"' AND invalidated_at IS NULL");
    assertThat(workflow.consume(CredentialWorkflow.Purpose.ACTIVATE,replacement.rawToken(),PASSWORD,corpus)).isFalse();
    assertThat(account(invited.userId(),"state||':'||credential_epoch")).isEqualTo("INVITED:0");
  }
  @Test @Tag("SEC-12") void resetIssuanceDoesNotDisableOrChangeTheCredential() throws Exception {
    var a=active();String before=account(a.userId(),"password_hash");
    var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);
    assertThat(account(a.userId(),"password_hash")).isEqualTo(before);
    assertThat(account(a.userId(),"state||':'||credential_epoch||':'||auth_transition_pending")).isEqualTo("ACTIVE:1:false");
    assertThat(reset.rawToken()).hasSize(43);
    assertThat(value("SELECT extract(epoch from expires_at-created_at)::int FROM identity.credential_token WHERE user_id='"+a.userId()+"' AND purpose='RESET' AND invalidated_at IS NULL")).isEqualTo("900");
  }
  @Test @Tag("SEC-12") void disableAndEnableNeverResurrectTokens() throws Exception {
    var a=active();var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
    assertThatThrownBy(()->workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET)).isInstanceOf(IllegalArgumentException.class);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.ACTIVE);
    assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,reset.rawToken(),PASSWORD,corpus)).isFalse();
    assertThat(account(a.userId(),"state||':'||credential_epoch||':'||auth_transition_pending")).isEqualTo("ACTIVE:3:true");
  }
  @Test @Tag("SEC-12") void recoveryRequiresNewCredentialAndEnableCannotBypassIt() throws Exception {
    var a=active();workflow.setState(a.userId(),CredentialWorkflow.AccountState.RECOVERY_REQUIRED);
    assertThat(account(a.userId(),"password_hash")).isNull();
    assertThatThrownBy(()->workflow.setState(a.userId(),CredentialWorkflow.AccountState.ACTIVE)).isInstanceOf(IllegalArgumentException.class);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
    assertThat(account(a.userId(),"state||':'||operator_disabled")).isEqualTo("RECOVERY_REQUIRED:true");
    assertThatThrownBy(()->workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET)).isInstanceOf(IllegalArgumentException.class);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.ACTIVE);
    assertThat(account(a.userId(),"state||':'||operator_disabled")).isEqualTo("RECOVERY_REQUIRED:false");
    var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);
    assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,reset.rawToken(),"fresh recovered password",corpus)).isTrue();
    assertThat(new Argon2CredentialHasher().matches(PASSWORD,account(a.userId(),"password_hash"))).isFalse();
    assertThat(account(a.userId(),"auth_transition_pending::text")).isEqualTo("true");
  }
  @Test @Tag("SEC-12") void recoveryOfDisabledAccountDoesNotBypassOperatorDisable() throws Exception {
    var a=active();workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.RECOVERY_REQUIRED);
    assertThat(account(a.userId(),"state||':'||operator_disabled")).isEqualTo("RECOVERY_REQUIRED:true");
    assertThat(account(a.userId(),"password_hash")).isNull();
    assertThatThrownBy(()->workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET)).isInstanceOf(IllegalArgumentException.class);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.ACTIVE);
    assertThat(account(a.userId(),"state||':'||operator_disabled")).isEqualTo("RECOVERY_REQUIRED:false");
    var token=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);
    assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),"fresh recovered password",corpus)).isTrue();
    var result=new CredentialVerifier(new Argon2CredentialHasher()).verifyAndApply(ds,throttle(100000),account(a.userId(),"login_name"),"fresh recovered password","127.0.0.9",(c,u,e)->{throw new AssertionError("Pending transition cannot allow an effect");});
    assertThat(result.accepted()).isFalse();
  }
  @Test @Tag("SEC-12") void disableDuringTokenKdfWinsWithoutHoldingDatabaseLocks() throws Exception {
    var a=active();var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);var paused=new PausedHasher(true);
    var custom=new CredentialWorkflow(ds,paused);
    var pool=Executors.newSingleThreadExecutor();
    try {
      var pending=pool.submit(()->custom.consume(CredentialWorkflow.Purpose.RESET,reset.rawToken(),"replacement password value",corpus));
      assertThat(paused.entered.await(5,TimeUnit.SECONDS)).isTrue();
      workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
      paused.release.countDown();assertThat(pending.get(10,TimeUnit.SECONDS)).isFalse();
      assertThat(account(a.userId(),"state||':'||credential_epoch")).isEqualTo("DISABLED:2");
    } finally {paused.release.countDown();stop(pool);}
  }
  @Test @Tag("SEC-12") void reissueDuringTokenKdfInvalidatesTheOldSnapshot() throws Exception {
    var a=active();var reset=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);var paused=new PausedHasher(true);
    var pool=Executors.newSingleThreadExecutor();
    try {
      var pending=pool.submit(()->new CredentialWorkflow(ds,paused).consume(CredentialWorkflow.Purpose.RESET,reset.rawToken(),PASSWORD,corpus));
      assertThat(paused.entered.await(5,TimeUnit.SECONDS)).isTrue();
      workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);
      paused.release.countDown();assertThat(pending.get(10,TimeUnit.SECONDS)).isFalse();
      assertThat(account(a.userId(),"credential_epoch::text")).isEqualTo("1");
    } finally {paused.release.countDown();stop(pool);}
  }
  @Test @Tag("SEC-12") void staleCredentialKdfAfterResetOrDisableNeverAppliesEffect() throws Exception {
    for(boolean reset:new boolean[]{true,false}) {
      var a=active();String login=account(a.userId(),"login_name");var paused=new PausedHasher(false);var effect=new AtomicInteger();
      var verifier=new CredentialVerifier(paused);var pool=Executors.newSingleThreadExecutor();
      try {
        var pending=pool.submit(()->verifier.verifyAndApply(ds,throttle(100000),login,PASSWORD,"127.0.0.2",(c,u,e)->effect.incrementAndGet()));
        assertThat(paused.entered.await(5,TimeUnit.SECONDS)).isTrue();
        if(reset){var token=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),"new reset password value",corpus)).isTrue();}
        else workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
        paused.release.countDown();assertThat(pending.get(10,TimeUnit.SECONDS).accepted()).isFalse();assertThat(effect.get()).isZero();
      } finally {paused.release.countDown();stop(pool);}
    }
  }
  @Test @Tag("SEC-12") void transactionFailureAfterTokenConsumeRollsEverythingBack() throws Exception {
    var a=active();var token=workflow.reissue(a.userId(),CredentialWorkflow.Purpose.RESET);String before=account(a.userId(),"password_hash");
    try(Connection c=source("migrator").getConnection();var s=c.createStatement()) {
      s.execute("CREATE FUNCTION identity.r03_fail_transition() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Controlled R03 rollback'; END $$");
      s.execute("CREATE TRIGGER r03_fail BEFORE INSERT ON identity.credential_transition FOR EACH ROW EXECUTE FUNCTION identity.r03_fail_transition()");
      try{assertThatThrownBy(()->workflow.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),"replacement password value",corpus)).isInstanceOf(SQLException.class);}
      finally{s.execute("DROP TRIGGER r03_fail ON identity.credential_transition");s.execute("DROP FUNCTION identity.r03_fail_transition()");}
    }
    assertThat(account(a.userId(),"password_hash")).isEqualTo(before);assertThat(account(a.userId(),"credential_epoch::text")).isEqualTo("1");
    assertThat(workflow.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),"replacement password value",corpus)).isTrue();
  }
  @Test @Tag("SEC-22") void genericAccountFailuresHaveIdenticalCodeAndMessage() throws Exception {
    var verifier=new CredentialVerifier(new Argon2CredentialHasher());var a=active();String login=account(a.userId(),"login_name");
    var unknown=verifier.verifyAndApply(ds,throttle(100000),name(),PASSWORD,"127.0.0.3",(c,u,e)->{});
    assertThat(verifier.verifyAndApply(ds,throttle(100000),login,"wrong password value","127.0.0.3",(c,u,e)->{})).isEqualTo(unknown);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.DISABLED);
    assertThat(verifier.verifyAndApply(ds,throttle(100000),login,PASSWORD,"127.0.0.3",(c,u,e)->{})).isEqualTo(unknown);
    workflow.setState(a.userId(),CredentialWorkflow.AccountState.RECOVERY_REQUIRED);
    assertThat(verifier.verifyAndApply(ds,throttle(100000),login,PASSWORD,"127.0.0.3",(c,u,e)->{})).isEqualTo(unknown);
  }
  @Test @Tag("SEC-22") void accountAndIpLimitsDenyBeforeKdf() throws Exception {
    var a=active();String login=account(a.userId(),"login_name");var counter=new AtomicInteger();var base=new Argon2CredentialHasher();
    PasswordHasher counted=new PasswordHasher(){public String hash(String p){return base.hash(p);}public boolean matches(String p,String h){counter.incrementAndGet();return base.matches(p,h);}};
    var verifier=new CredentialVerifier(counted);var t=throttle(100000);
    for(int i=0;i<5;i++)assertThat(verifier.verifyAndApply(ds,t,login,"wrong password value","127.0.0.4",(c,u,e)->{}).code()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(verifier.verifyAndApply(ds,t,login,PASSWORD,"127.0.0.4",(c,u,e)->{}).code()).isEqualTo("THROTTLED");assertThat(counter.get()).isEqualTo(5);
    for(int i=0;i<30;i++)assertThat(t.recordFailure(AuthThrottle.Kind.IP,"127.0.0.5")).isEqualTo(i==29?AuthThrottle.Decision.BLOCKED:AuthThrottle.Decision.ALLOW);
    assertThat(t.beforeKdf(AuthThrottle.Kind.IP,"127.0.0.5")).isEqualTo(AuthThrottle.Decision.BLOCKED);
  }
  @Test @Tag("SEC-22") void capIsAtomicForConcurrentNewKeysAndExistingKeysStillWork() throws Exception {
    var t=throttle(100000);String existing=name();assertThat(t.beforeKdf(AuthThrottle.Kind.ACCOUNT,existing)).isEqualTo(AuthThrottle.Decision.ALLOW);t.complete(List.of(new AuthThrottle.Key(AuthThrottle.Kind.ACCOUNT,existing)),false);
    int size=Integer.parseInt(value("SELECT count(*) FROM identity.auth_throttle"));var limited=throttle(size+1);var gate=new CyclicBarrier(2);
    var decisions=two(()->{gate.await();return limited.beforeKdf(AuthThrottle.Kind.ACCOUNT,name());});
    assertThat(decisions).containsExactlyInAnyOrder(AuthThrottle.Decision.ALLOW,AuthThrottle.Decision.CAPACITY_REJECTED);
    assertThat(Integer.parseInt(value("SELECT count(*) FROM identity.auth_throttle"))).isEqualTo(size+1);
    assertThat(limited.beforeKdf(AuthThrottle.Kind.ACCOUNT,existing)).isEqualTo(AuthThrottle.Decision.ALLOW);
  }
  @Test @Tag("SEC-22") void expiredBucketsAreDeletedAndTemporaryBlockDoesNotPermanentlyLockAccount() throws Exception {
    var t=throttle(100000);String key=name();for(int i=0;i<5;i++)t.recordFailure(AuthThrottle.Kind.ACCOUNT,key);
    assertThat(t.beforeKdf(AuthThrottle.Kind.ACCOUNT,key)).isEqualTo(AuthThrottle.Decision.BLOCKED);
    try(Connection c=ds.getConnection();var s=c.prepareStatement("UPDATE identity.auth_throttle SET window_started_at=clock_timestamp()-interval '16 minutes',blocked_until=clock_timestamp()-interval '1 second' WHERE key_digest=? AND bucket_kind='ACCOUNT'")){s.setBytes(1,t.digest(AuthThrottle.Kind.ACCOUNT,key));s.executeUpdate();}
    assertThat(t.beforeKdf(AuthThrottle.Kind.ACCOUNT,key)).isEqualTo(AuthThrottle.Decision.ALLOW);t.complete(List.of(new AuthThrottle.Key(AuthThrottle.Kind.ACCOUNT,key)),false);
    try(Connection c=ds.getConnection();var s=c.prepareStatement("UPDATE identity.auth_throttle SET expires_at=clock_timestamp()-interval '1 second' WHERE key_digest=?")){s.setBytes(1,t.digest(AuthThrottle.Kind.ACCOUNT,key));s.executeUpdate();}
    assertThat(t.beforeKdf(AuthThrottle.Kind.ACCOUNT,key)).isEqualTo(AuthThrottle.Decision.ALLOW);
  }
  @Test @Tag("SEC-22") void kdfFailureIsUnavailableForKnownAndUnknownAccountsAndReleasesReservations() throws Exception {
    var a=active();String login=account(a.userId(),"login_name");PasswordHasher broken=new PasswordHasher(){public String hash(String p){throw new IllegalStateException("KDF failure");}public boolean matches(String p,String h){throw new IllegalStateException("KDF failure");}};
    var verifier=new CredentialVerifier(broken);var t=throttle(100000);
    var known=verifier.verifyAndApply(ds,t,login,PASSWORD,"127.0.0.6",(c,u,e)->{throw new AssertionError();});
    assertThat(known.code()).isEqualTo("UNAVAILABLE");
    assertThat(verifier.verifyAndApply(ds,t,name(),PASSWORD,"127.0.0.6",(c,u,e)->{})).isEqualTo(known);
    assertThat(value("SELECT sum(in_flight)::text FROM identity.auth_throttle WHERE bucket_kind='IP' AND key_digest=decode('"+HexFormat.of().formatHex(t.digest(AuthThrottle.Kind.IP,"127.0.0.6"))+"','hex')")).isEqualTo("0");
  }
  @Test @Tag("SEC-22") void tokenBoundaryThrottlePrecedesPasswordHash() throws Exception {
    var calls=new AtomicInteger();var base=new Argon2CredentialHasher();PasswordHasher counted=new PasswordHasher(){public String hash(String p){calls.incrementAndGet();return base.hash(p);}public boolean matches(String p,String h){return base.matches(p,h);}};
    var a=workflow.invite(name());var t=throttle(100000);var service=new CredentialService(new CredentialWorkflow(ds,counted),corpus,t);
    assertThat(service.consume(CredentialWorkflow.Purpose.ACTIVATE,a.rawToken(),PASSWORD,"127.0.0.7").accepted()).isTrue();
    for(int i=0;i<5;i++)assertThat(service.consume(CredentialWorkflow.Purpose.ACTIVATE,a.rawToken(),PASSWORD,"127.0.0.7").accepted()).isFalse();
    int before=calls.get();assertThat(service.consume(CredentialWorkflow.Purpose.ACTIVATE,a.rawToken(),PASSWORD,"127.0.0.7").code()).isEqualTo("THROTTLED");assertThat(calls.get()).isEqualTo(before);
  }
  @Test @Tag("SEC-22") void auditIsAppendOnlyAndContainsNoTokenOrPassword() throws Exception {
    var a=active();
    assertThat(value("SELECT count(*) FROM identity.security_audit WHERE target_user_id='"+a.userId()+"'")).isEqualTo("2");
    try(Connection c=ds.getConnection();var s=c.prepareStatement("SELECT string_agg(row_to_json(a)::text,',') FROM identity.security_audit a WHERE target_user_id=?")){s.setObject(1,a.userId());try(var r=s.executeQuery()){r.next();assertThat(r.getString(1)).doesNotContain(PASSWORD,a.rawToken());}}
    try(Connection c=ds.getConnection();var s=c.createStatement()){assertThatThrownBy(()->s.executeUpdate("DELETE FROM identity.security_audit")).isInstanceOf(SQLException.class);}
  }
  @Test @Tag("SEC-22") void databaseCapacityGuardRejectsBypassingTheJavaAdmission() throws Exception {
    int size=Integer.parseInt(value("SELECT count(*) FROM identity.auth_throttle"));
    try(Connection c=source("migrator").getConnection();var s=c.createStatement()) {
      s.executeUpdate("UPDATE identity.auth_throttle_capacity SET max_keys="+(size+1));
      try {
        var gate=new CyclicBarrier(2);
        assertThat(two(()->{
          gate.await();
          try(Connection app=ds.getConnection();var insert=app.prepareStatement("INSERT INTO identity.auth_throttle(key_digest,bucket_kind,window_started_at,failed_count,expires_at) VALUES (?,'ACCOUNT',clock_timestamp(),0,clock_timestamp()+interval '30 minutes')")) {
            insert.setBytes(1,throttle(100000).digest(AuthThrottle.Kind.ACCOUNT,name()));
            try {insert.executeUpdate();return true;}
            catch(SQLException capacity){assertThat(capacity.getSQLState()).isEqualTo("P0001");return false;}
          }
        })).containsExactlyInAnyOrder(true,false);
        assertThat(Integer.parseInt(value("SELECT count(*) FROM identity.auth_throttle"))).isEqualTo(size+1);
        try(Connection app=ds.getConnection();var change=app.createStatement()) {
          assertThatThrownBy(()->change.executeUpdate("UPDATE identity.auth_throttle_capacity SET max_keys=100000")).isInstanceOf(SQLException.class);
        }
      } finally {s.executeUpdate("UPDATE identity.auth_throttle_capacity SET max_keys=100000");}
    }
  }
  @Test @Tag("SEC-22") void twoKdfSlotsRejectAnAdditionalAttemptWithoutAnUnboundedQueue() throws Exception {
    var a=active();var b=active();var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var calls=new AtomicInteger();
    PasswordHasher paused=new PasswordHasher(){
      public String hash(String p){throw new AssertionError();}
      public boolean matches(String p,String h){calls.incrementAndGet();entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return new Argon2CredentialHasher().matches(p,h);}
    };
    var verifier=new CredentialVerifier(paused);var t=throttle(100000);var pool=Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->verifier.verifyAndApply(ds,t,account(a.userId(),"login_name"),PASSWORD,"127.0.0.10",(c,u,e)->{}));
      var second=pool.submit(()->verifier.verifyAndApply(ds,t,account(b.userId(),"login_name"),PASSWORD,"127.0.0.10",(c,u,e)->{}));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      assertThat(verifier.verifyAndApply(ds,t,name(),PASSWORD,"127.0.0.10",(c,u,e)->{throw new AssertionError();}).code()).isEqualTo("THROTTLED");
      assertThat(calls.get()).isEqualTo(2);
      release.countDown();assertThat(first.get(10,TimeUnit.SECONDS).accepted()).isTrue();assertThat(second.get(10,TimeUnit.SECONDS).accepted()).isTrue();
    } finally {release.countDown();stop(pool);}
  }

  static CredentialWorkflow.IssuedToken active() throws Exception {var a=workflow.invite(name());assertThat(workflow.consume(CredentialWorkflow.Purpose.ACTIVATE,a.rawToken(),PASSWORD,corpus)).isTrue();return a;}
  static String name(){return "u"+UUID.randomUUID().toString().replace("-","").substring(0,20);}
  static PGSimpleDataSource source(String role) {var source=new PGSimpleDataSource();source.setServerNames(new String[]{"127.0.0.1"});source.setPortNumbers(new int[]{Integer.parseInt(CONFIG.getProperty("lab.port"))});source.setDatabaseName("identity_db");source.setUser(CONFIG.getProperty("identity."+role+".user"));source.setPassword(CONFIG.getProperty("identity."+role+".password"));source.setConnectTimeout(1);source.setSocketTimeout(5);source.setSslMode("disable");return source;}
  static AuthThrottle throttle(int cap){return new AuthThrottle(ds,"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),cap);}
  static String account(UUID user,String expression) throws SQLException{return value("SELECT "+expression+" FROM identity.app_user WHERE user_id='"+user+"'");}
  static String value(String sql) throws SQLException {try(Connection c=ds.getConnection()){return scalar(c,sql);}}
  static String scalar(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
  static void update(String sql) throws SQLException {try(Connection c=ds.getConnection();var s=c.createStatement()){s.executeUpdate(sql);}}
  static <T> List<T> two(Callable<T> task) throws Exception {var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(task);var b=pool.submit(task);return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}finally{stop(pool);}}
  static void stop(ExecutorService pool) throws Exception {pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  static final class PausedHasher implements PasswordHasher {
    final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final boolean pauseHash;final Argon2CredentialHasher base=new Argon2CredentialHasher();
    PausedHasher(boolean pauseHash){this.pauseHash=pauseHash;}
    void pause(){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted");}}
    public String hash(String p){if(pauseHash)pause();return base.hash(p);}
    public boolean matches(String p,String h){if(!pauseHash)pause();return base.matches(p,h);}
  }
}
