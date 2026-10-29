package lab.backend.identity.credential;

import static org.assertj.core.api.Assertions.*;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import lab.backend.identity.session.SessionAuthority;
import lab.backend.identity.session.AuthBarrierCoordinator;
import lab.backend.identity.session.AuthPendingWork;
import lab.backend.identity.session.ReauthAuthority;
import lab.backend.identity.session.IdentitySensitiveAuthority;
import lab.backend.identity.session.IdentitySensitiveIntent;
import lab.backend.identity.delivery.AuthDeliveryStore;
import lab.backend.identity.delivery.AuthDeliveryWire;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionOperations;

/** Actual own PostgreSQL cho R04a và canonical authority R04d.
 * Không dùng test DB trực tiếp này thay mTLS/browser/receiver qualification. */
@Timeout(90)
class IdentitySessionAuthorityIT {
  static final Properties CONFIG=new Properties();
  static final UUID REALM=UUID.randomUUID();
  static final String PASSWORD="  密碼🔐 correct password value  ";
  static PGSimpleDataSource ds,migrator;
  static SessionAuthority authority;
  static CredentialWorkflow credentials;
  static PasswordCorpus corpus;
  static final Map<String,UUID> GENERATIONS=Map.of("identity",UUID.randomUUID(),"content",UUID.randomUUID(),"learning",UUID.randomUUID());
  static AuthBarrierCoordinator barrier;
  static SessionAuthority coordinated;
  static ReauthAuthority reauth;
  static IdentitySensitiveAuthority sensitive;
  static CredentialVerifier canonicalVerifier;
  static final byte[] SENSITIVE_KEY=new byte[32];
  record Account(UUID user,String login) {}
  @BeforeAll static void setup() throws Exception {
    String input=System.getProperty("lab.config");
    if(input==null||input.isBlank()||input.startsWith("${"))throw new IllegalStateException("R04a requires actual managed PostgreSQL");
    Path path=Path.of(input).toRealPath();
    try(Reader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){CONFIG.load(r);}
    ds=source("app");migrator=source("migrator");credentials=new CredentialWorkflow(ds);
    corpus=PasswordCorpus.load(CredentialPolicyTests.corpusPath(),CredentialPolicyTests.HASH);
    // DB suite runs first and proves fresh V1/V2/V3/test V20 migration.
    execute(migrator,"INSERT INTO identity.auth_environment VALUES (true,'"+REALM+"','r1-static-v1')");
    var throttle=new AuthThrottle(ds,new byte[32],100000);
    authority=new SessionAuthority(ds,REALM,new CredentialVerifier(new Argon2CredentialHasher()),
        throttle,new CredentialService(credentials,corpus,throttle));
    // Own lab provisioning explicit: không runtime auto-create realm/generation.
    execute(migrator,"INSERT INTO identity.delivery_environment(singleton,realm_id,db_generation,identity_generation,content_generation,learning_generation,ready) VALUES (true,'"+REALM+"','"+GENERATIONS.get("identity")+"','"+GENERATIONS.get("identity")+"','"+GENERATIONS.get("content")+"','"+GENERATIONS.get("learning")+"',true)");
    barrier=new AuthBarrierCoordinator(ds,REALM,GENERATIONS.get("identity"),GENERATIONS);
    canonicalVerifier=new CredentialVerifier(new Argon2CredentialHasher());
    coordinated=new SessionAuthority(ds,REALM,canonicalVerifier,throttle,
        new CredentialService(credentials,corpus,throttle),barrier);
    reauth=new ReauthAuthority(ds,REALM,coordinated,barrier);
    Arrays.fill(SENSITIVE_KEY,(byte)1);
    sensitive=new IdentitySensitiveAuthority(ds,REALM,reauth,barrier,canonicalVerifier,corpus,SENSITIVE_KEY);
  }
  @AfterAll static void finishBarrierEnvironment() throws Exception {
    // Suite delivery tiếp theo provision realm khác; chỉ own operator fixture
    // xóa environment test, không runtime reset/cleanup receipt/security history.
    if(migrator!=null)execute(migrator,"DELETE FROM identity.delivery_environment");
  }

  @Test @Tag("SEC-21") void preauthStoresOnlyHandleDigestAndRedactsSecrets() throws Exception {
    var flow=authority.begin(null);
    assertThat(flow.cookie()).hasSize(43);assertThat(flow.view().csrfToken()).hasSize(43);
    assertThat(flow.toString()+flow.view()).doesNotContain(flow.cookie(),flow.view().csrfToken());
    assertThat(authority.bootstrap(flow.cookie())).isEqualTo(flow.view());
    assertThat(authority.begin(flow.cookie()).cookie()).isNull();
    assertThat(value("SELECT encode(cookie_digest,'hex') FROM identity.auth_preauth WHERE context_id='"+flow.view().contextId()+"'"))
        .isEqualTo(HexFormat.of().formatHex(SessionAuthority.digest(flow.cookie())));
    assertThat(value("SELECT extract(epoch from expires_at-created_at)::int FROM identity.auth_preauth WHERE context_id='"+flow.view().contextId()+"'"))
        .isEqualTo("900");
  }
  @Test @Tag("SEC-21") void loginRotatesCookieContextAndCsrfWithoutFrameworkAttributes() throws Exception {
    Account a=account();var flow=authority.begin(null);var session=login(a,flow);
    assertThat(session.cookie()).isNotEqualTo(flow.cookie());
    assertThat(session.view().contextId()).isNotEqualTo(flow.view().contextId());
    assertThat(session.view().csrfToken()).isNotEqualTo(flow.view().csrfToken());
    assertThat(session.maxAge()).isEqualTo(43200);
    assertThat(authority.bootstrap(session.cookie())).isEqualTo(session.view());
    assertThat(authority.bootstrap(flow.cookie()).state()).isEqualTo("ANONYMOUS");
    assertThat(value("SELECT framework_id FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"))
        .isEqualTo(HexFormat.of().formatHex(SessionAuthority.digest(session.cookie())));
    assertThat(value("SELECT count(*) FROM identity.spring_session_attributes")).isEqualTo("0");
    denial(409,()->login(a,flow));
  }
  @Test @Tag("SEC-21") void twoAccountsCannotConsumeOnePreauth() throws Exception {
    Account a=account(),b=account();var flow=authority.begin(null);var gate=new CyclicBarrier(2);
    ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results=new ArrayList<>();
      for(Account next:List.of(a,b))results.add(pool.submit(()->{gate.await();try{login(next,flow);return true;}catch(Denied denied){assertThat(denied.status).isEqualTo(409);return false;}}));
      assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id IN ('"+a.user+"','"+b.user+"')")).isEqualTo("1");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-21") void wrongContextAndCsrfDoNotReachCredentialThrottle() throws Exception {
    var flow=authority.begin(null);long before=Long.parseLong(value("SELECT count(*) FROM identity.auth_throttle"));
    denial(403,()->authority.login(flow.cookie(),flow.view().contextId(),"x".repeat(43),"unknown",PASSWORD,"127.0.0.1"));
    denial(409,()->authority.login(flow.cookie(),UUID.randomUUID(),flow.view().csrfToken(),"unknown",PASSWORD,"127.0.0.1"));
    assertThat(value("SELECT count(*) FROM identity.auth_throttle")).isEqualTo(Long.toString(before));
  }
  @Test @Tag("SEC-22") void unknownWrongDisabledAndPendingLoginAreGenericAndCannotCreateSessions() throws Exception {
    Account wrong=account(),disabled=account(),pending=account();
    credentials.setState(disabled.user,CredentialWorkflow.AccountState.DISABLED);
    execute(ds,"UPDATE identity.app_user SET auth_transition_pending=true WHERE user_id='"+pending.user+"'");
    for(String name:List.of("unknown"+UUID.randomUUID().toString().substring(0,8),wrong.login,disabled.login,pending.login)) {
      var flow=authority.begin(null);
      String password=name.equals(wrong.login)?"valid length wrong password":PASSWORD;
      assertThatThrownBy(()->authority.login(flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),name,password,"127.1."+(Math.abs(name.hashCode())%200)+".1"))
          .isInstanceOfSatisfying(Denied.class,e->{assertThat(e.status).isEqualTo(401);assertThat(e.code).isEqualTo("INVALID_CREDENTIALS");});
    }
    assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id IN ('"+wrong.user+"','"+disabled.user+"','"+pending.user+"')")).isEqualTo("0");
  }
  @Test @Tag("SEC-05") void backingWriteFailureRollsBackPreauthAndCanonicalSession() throws Exception {
    Account a=account();var flow=authority.begin(null);
    execute(migrator,"CREATE FUNCTION identity.test_backing_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END $$");
    execute(migrator,"CREATE TRIGGER test_backing_failure BEFORE INSERT ON identity.spring_session FOR EACH ROW EXECUTE FUNCTION identity.test_backing_failure()");
    try {
      denial(503,()->login(a,flow));
      assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(authority.bootstrap(flow.cookie()).csrfToken()).isEqualTo(flow.view().csrfToken());
    }finally {
      execute(migrator,"DROP TRIGGER test_backing_failure ON identity.spring_session");
      execute(migrator,"DROP FUNCTION identity.test_backing_failure()");
    }
    assertThat(login(a,flow).view().state()).isEqualTo("ACTIVE");
  }
  @Test @Tag("SEC-11") void logoutIsPendingAtomicAndStatusRequiresInitiatingCookieAndContext() throws Exception {
    Account a=account(),b=account();var session=login(a,authority.begin(null));var other=login(b,authority.begin(null));
    UUID key=UUID.randomUUID(),op=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),key);
    assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("PENDING_FENCES");
    assertThat(authority.bootstrap(session.cookie()).operationId()).isEqualTo(op);
    assertThat(authority.bootstrap(session.cookie()).userId()).isNull();
    assertThat(value("SELECT count(*) FROM identity.auth_fence_intent WHERE operation_id='"+op+"'")).isEqualTo("2");
    assertThat(value("SELECT count(*) FROM identity.spring_session WHERE session_id='"+framework(session)+"'")).isEqualTo("0");
    assertThat(authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),key)).isEqualTo(op);
    denial(404,()->authority.operationStatus(other.cookie(),session.view().contextId(),op));
    denial(404,()->authority.operationStatus(session.cookie(),other.view().contextId(),op));
    denial(409,()->authority.logout(other.cookie(),session.view().contextId(),session.view().csrfToken(),key));
    assertThat(authority.bootstrap(other.cookie()).state()).isEqualTo("ACTIVE");
    denial(409,()->authority.begin(session.cookie()));
  }
  @Test @Tag("SEC-05") void lateFrameworkSaveAndOrphanSessionCannotRestoreAuthority() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    try(Connection c=ds.getConnection()) {
      var repository=backing(c,framework(session));Session late=repository.findById(framework(session));
      assertThat(late).isNotNull();
      authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
      late.setLastAccessedTime(Instant.now());repository.save(late);
      // Force a brand new stale backing row as well; even INSERT cannot grant.
      Session orphan=repository.createSession();orphan.setMaxInactiveInterval(Duration.ofHours(12));repository.save(orphan);
      assertThat(value("SELECT count(*) FROM identity.spring_session WHERE session_id='"+framework(session)+"'")).isEqualTo("1");
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKING");
      denial(401,()->authority.interactive(session.cookie(),session.view().contextId(),session.view().csrfToken()));
      denial(23514,()->execute(ds,"UPDATE identity.auth_session SET state='ACTIVE',csrf_token='"+session.view().csrfToken()+"' WHERE context_id='"+session.view().contextId()+"'"));
    }
  }
  @Test @Tag("SEC-05") void logoutIntentFailureRollsBackCanonicalAndFrameworkRows() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    execute(migrator,"CREATE FUNCTION identity.test_intent_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.participant='learning' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_intent_failure BEFORE INSERT ON identity.auth_fence_intent FOR EACH ROW EXECUTE FUNCTION identity.test_intent_failure()");
    try {
      assertThatThrownBy(()->authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID())).isInstanceOf(SQLException.class);
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
    }finally {
      execute(migrator,"DROP TRIGGER test_intent_failure ON identity.auth_fence_intent");
      execute(migrator,"DROP FUNCTION identity.test_intent_failure()");
    }
  }
  @Test @Tag("SEC-10") void pollingNeverTouchesIdleAndExplicitUserActionDoes() throws Exception {
    var session=login(account(),authority.begin(null));
    String before=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    for(int i=0;i<3;i++)authority.bootstrap(session.cookie());
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")).isEqualTo(before);
    authority.interactive(session.cookie(),session.view().contextId(),session.view().csrfToken());
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")).isNotEqualTo(before);
  }
  @Test @Tag("SEC-10") void idleAndAbsoluteExpiryDenyWithoutSchedulerAndRemainTerminal() throws Exception {
    for(boolean absolute:List.of(false,true)) {
      Account a=account();var seed=aged(a,absolute?Duration.ofHours(13):Duration.ofMinutes(31),absolute?Duration.ZERO:Duration.ofMinutes(31));
      var expired=authority.bootstrap(seed.cookie());assertThat(expired.state()).isEqualTo("EXPIRED");assertThat(expired.operationId()).isNotNull();
      assertThat(authority.operationStatus(seed.cookie(),seed.view().contextId(),expired.operationId())).isEqualTo("PENDING_FENCES");
      denial(401,()->authority.interactive(seed.cookie(),seed.view().contextId(),seed.view().csrfToken()));
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("1");
    }
  }
  @Test @Tag("SEC-05") void disableAfterLoginCanonicallyDeniesBeforeAnyReceiverExists() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    credentials.setState(a.user,CredentialWorkflow.AccountState.DISABLED);
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("EXPIRED");
    denial(401,()->authority.interactive(session.cookie(),session.view().contextId(),session.view().csrfToken()));
    assertThat(value("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("true");
  }
  @Test @Tag("SEC-10") void wrongRealmOrUnavailableDbIsNeverAnonymous() throws Exception {
    var other=new SessionAuthority(ds,UUID.randomUUID(),new CredentialVerifier(new Argon2CredentialHasher()),new AuthThrottle(ds,new byte[32],100000));
    denial(503,()->other.bootstrap(null));
    PGSimpleDataSource closed=source("app");closed.setPortNumbers(new int[]{1});closed.setConnectTimeout(1);
    var unavailable=new SessionAuthority(closed,REALM,new CredentialVerifier(new Argon2CredentialHasher()),new AuthThrottle(closed,new byte[32],100000));
    assertThatThrownBy(()->unavailable.bootstrap(null)).isInstanceOf(SQLException.class);
  }
  @Test @Tag("SEC-05") void appCannotDeleteTerminalGuardsOrRewriteOperationAndEnvironment() throws Exception {
    var flow=authority.begin(null);var session=login(account(),flow);
    UUID op=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    for(String sql:List.of("DELETE FROM identity.auth_session","DELETE FROM identity.auth_preauth",
        "UPDATE identity.auth_operation SET idempotency_key='"+UUID.randomUUID()+"' WHERE operation_id='"+op+"'",
        "UPDATE identity.auth_environment SET realm_id='"+UUID.randomUUID()+"'","TRUNCATE identity.spring_session CASCADE")) {
      denial(42501,()->execute(ds,sql));
    }
    // State có column grant cho coordinator, nhưng trigger vẫn deny no-op/rewrite.
    denial(23514,()->execute(ds,"UPDATE identity.auth_operation SET state='PENDING_FENCES' WHERE operation_id='"+op+"'"));
    denial(23514,()->execute(ds,"UPDATE identity.auth_preauth SET consumed_at=NULL,csrf_token='"+flow.view().csrfToken()+"' WHERE context_id='"+flow.view().contextId()+"'"));
  }
  @Test @Tag("SEC-05") void accountLockOrdersLogoutAfterAcceptedUserAction() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try(Connection first=ds.getConnection()) {
      first.setAutoCommit(false);
      try(var s=first.prepareStatement("SELECT user_id FROM identity.app_user WHERE user_id=? FOR UPDATE")){s.setObject(1,a.user);s.executeQuery().close();}
      var started=new CountDownLatch(1);
      Future<UUID> logout=pool.submit(()->{started.countDown();return authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());});
      assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
      // Observe the actual server-side blocked lock, not a guessed sleep.
      boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
      while(System.nanoTime()<deadline) {
        if(Integer.parseInt(value("SELECT count(*) FROM pg_stat_activity WHERE datname='identity_db' AND wait_event_type='Lock'"))>0){waiting=true;break;}
        Thread.onSpinWait();
      }
      assertThat(waiting).isTrue();assertThat(logout.isDone()).isFalse();first.commit();
      UUID op=logout.get(10,TimeUnit.SECONDS);
      assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("PENDING_FENCES");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  @Test @Tag("SEC-21") void disableDuringLoginKdfCannotCommitAStaleSessionOrConsumePreauth() throws Exception {
    Account a=account();var flow=authority.begin(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    PasswordHasher paused=new PasswordHasher() {
      final Argon2CredentialHasher delegate=new Argon2CredentialHasher();
      public String hash(String value){return delegate.hash(value);}
      public boolean matches(String value,String encoded) {
        entered.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("fixture interrupted");}
        return delegate.matches(value,encoded);
      }
    };
    var custom=new SessionAuthority(ds,REALM,new CredentialVerifier(paused),new AuthThrottle(ds,new byte[32],100000));
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      Future<SessionAuthority.Issued> result=pool.submit(()->custom.login(flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),a.login,PASSWORD,"127.9.0.1"));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      credentials.setState(a.user,CredentialWorkflow.AccountState.DISABLED); // would time out if KDF held account lock
      release.countDown();
      assertThatThrownBy(()->result.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(Denied.class);
      assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(authority.bootstrap(flow.cookie()).csrfToken()).isEqualTo(flow.view().csrfToken());
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-21") void preauthCapacityIsAtomicAndCannotBeBypassedByDirectInsert() throws Exception {
    var existing=authority.begin(null);long before=Long.parseLong(value("SELECT count(*) FROM identity.auth_preauth"));
    execute(migrator,"UPDATE identity.auth_preauth_capacity SET max_flows="+(before+1));
    ExecutorService pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
    try {
      Callable<Boolean> attempt=()->{gate.await();try{authority.begin(null);return true;}catch(Denied e){assertThat(e.status).isEqualTo(429);return false;}};
      Future<Boolean> a=pool.submit(attempt),b=pool.submit(attempt);
      assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(value("SELECT count(*) FROM identity.auth_preauth")).isEqualTo(Long.toString(before+1));
      assertThat(authority.begin(existing.cookie()).cookie()).isNull();
      denial(10001,()->execute(ds,"INSERT INTO identity.auth_preauth(cookie_digest,context_id,realm_id,csrf_token) VALUES (decode(repeat('f',64),'hex'),'"+UUID.randomUUID()+"','"+REALM+"',repeat('z',43))"));
      denial(42501,()->execute(ds,"UPDATE identity.auth_preauth_capacity SET max_flows=100000"));
    }finally {execute(migrator,"UPDATE identity.auth_preauth_capacity SET max_flows=100000");pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-10") void expiredPreauthNeverReopensOrReachesKdf() throws Exception {
    String raw="v".repeat(43),csrf="w".repeat(43);UUID context=UUID.randomUUID();
    execute(ds,"INSERT INTO identity.auth_preauth(cookie_digest,context_id,realm_id,csrf_token,created_at,expires_at) VALUES (decode('"+HexFormat.of().formatHex(SessionAuthority.digest(raw))+"','hex'),'"+context+"','"+REALM+"','"+csrf+"',clock_timestamp()-interval '16 minutes',clock_timestamp()-interval '1 minute')");
    long before=Long.parseLong(value("SELECT count(*) FROM identity.auth_throttle"));
    denial(409,()->authority.login(raw,context,csrf,"unknown",PASSWORD,"127.9.0.1"));
    assertThat(value("SELECT count(*) FROM identity.auth_throttle")).isEqualTo(Long.toString(before));
    assertThat(authority.bootstrap(raw).state()).isEqualTo("ANONYMOUS");
    var replacement=authority.begin(raw);assertThat(replacement.cookie()).isNotEqualTo(raw);assertThat(replacement.view().contextId()).isNotEqualTo(context);
  }

  @Test @Tag("SEC-12") void activationConsumesCredentialAndPreauthAtomicallyWithoutAutoLogin() throws Exception {
    var token=credentials.invite("activate"+UUID.randomUUID().toString().replace("-","").substring(0,16));var flow=authority.begin(null);
    var result=consume(authority,CredentialWorkflow.Purpose.ACTIVATE,token,flow,UUID.randomUUID());
    assertThat(result.state()).isEqualTo("ACTIVATED");assertThat(result.operationId()).isNull();
    assertThat(value("SELECT state||':'||credential_epoch::text FROM identity.app_user WHERE user_id='"+token.userId()+"'")).isEqualTo("ACTIVE:1");
    assertThat(value("SELECT consumed_at IS NOT NULL FROM identity.auth_preauth WHERE context_id='"+flow.view().contextId()+"'")).isEqualTo("t");
    assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+token.userId()+"'")).isEqualTo("0");
    assertThat(authority.bootstrap(flow.cookie()).contextId()).isNull();
    denial(409,()->consume(authority,CredentialWorkflow.Purpose.ACTIVATE,token,flow,UUID.randomUUID()));
    var fresh=authority.begin(flow.cookie());assertThat(fresh.cookie()).isNotEqualTo(flow.cookie());
  }
  @Test @Tag("SEC-12") void resetBindsLostResponseStatusAndDeniesAllExistingSessionsWithoutCompletion() throws Exception {
    Account a=account();var first=login(a,authority.begin(null));var second=login(a,authority.begin(null));
    var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);var flow=authority.begin(null);UUID key=UUID.randomUUID();
    String changed="new 密碼🔐 password after reset";
    var result=authority.consume(CredentialWorkflow.Purpose.RESET,flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),token.rawToken(),changed,"127.12.0.1",key);UUID op=result.operationId();
    assertThat(result.state()).isEqualTo("PENDING_FENCES");
    assertThat(authority.bootstrap(flow.cookie()).operationId()).isEqualTo(op);
    assertThat(authority.bootstrap(flow.cookie()).csrfToken()).isNull();
    assertThat(authority.operationStatus(flow.cookie(),flow.view().contextId(),op)).isEqualTo("PENDING_FENCES");
    for(var session:List.of(first,second)) {
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKING");
      denial(401,()->authority.interactive(session.cookie(),session.view().contextId(),session.view().csrfToken()));
      denial(404,()->authority.operationStatus(session.cookie(),flow.view().contextId(),op));
    }
    denial(404,()->authority.operationStatus(flow.cookie(),UUID.randomUUID(),op));
    denial(409,()->consume(authority,CredentialWorkflow.Purpose.RESET,token,flow,key));
    denial(409,()->authority.begin(flow.cookie()));
    assertThat(value("SELECT credential_epoch||':'||auth_transition_pending::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("2:true");
    String hash=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    assertThat(new Argon2CredentialHasher().matches(changed,hash)).isTrue();assertThat(new Argon2CredentialHasher().matches(PASSWORD,hash)).isFalse();
    assertThat(value("SELECT count(*) FROM identity.auth_fence_intent WHERE operation_id='"+op+"'")).isEqualTo("2");
    assertThat(value("SELECT count(*) FROM identity.credential_transition WHERE operation_id='"+op+"' AND action='RESET'")).isEqualTo("1");
    assertThat(value("SELECT count(*) FROM identity.spring_session WHERE session_id IN ('"+framework(first)+"','"+framework(second)+"')")).isEqualTo("0");
    var attempt=authority.begin(null);denial(401,()->login(a,attempt)); // pending is never cleared locally
  }
  @Test @Tag("SEC-12") void failedSecondResetIntentRollsBackPasswordTokenPreauthAndSessions() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));var flow=authority.begin(null);
    var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    String before=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    execute(migrator,"CREATE FUNCTION identity.test_reset_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.participant='learning' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_reset_failure BEFORE INSERT ON identity.auth_fence_intent FOR EACH ROW EXECUTE FUNCTION identity.test_reset_failure()");
    try {
      denial(503,()->consume(authority,CredentialWorkflow.Purpose.RESET,token,flow,UUID.randomUUID()));
      assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(before);
      assertThat(value("SELECT consumed_at IS NULL FROM identity.credential_token WHERE user_id='"+a.user+"' AND purpose='RESET'")).isEqualTo("t");
      assertThat(value("SELECT count(*) FROM identity.credential_transition WHERE user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(authority.bootstrap(flow.cookie())).isEqualTo(flow.view());
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
    }finally {
      execute(migrator,"DROP TRIGGER test_reset_failure ON identity.auth_fence_intent");execute(migrator,"DROP FUNCTION identity.test_reset_failure()");
    }
    assertThat(consume(authority,CredentialWorkflow.Purpose.RESET,token,flow,UUID.randomUUID()).operationId()).isNotNull();
  }
  @Test @Tag("SEC-21") void activationPreauthWriteFailureRollsBackCredentialConsume() throws Exception {
    var token=credentials.invite("rollback"+UUID.randomUUID().toString().replace("-","").substring(0,16));var flow=authority.begin(null);
    execute(migrator,"CREATE FUNCTION identity.test_preauth_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END $$");
    execute(migrator,"CREATE TRIGGER test_preauth_failure BEFORE UPDATE ON identity.auth_preauth FOR EACH ROW EXECUTE FUNCTION identity.test_preauth_failure()");
    try {
      denial(503,()->consume(authority,CredentialWorkflow.Purpose.ACTIVATE,token,flow,null));
      assertThat(value("SELECT state FROM identity.app_user WHERE user_id='"+token.userId()+"'")).isEqualTo("INVITED");
      assertThat(value("SELECT consumed_at IS NULL FROM identity.credential_token WHERE user_id='"+token.userId()+"'")).isEqualTo("t");
      assertThat(authority.bootstrap(flow.cookie())).isEqualTo(flow.view());
    }finally {
      execute(migrator,"DROP TRIGGER test_preauth_failure ON identity.auth_preauth");execute(migrator,"DROP FUNCTION identity.test_preauth_failure()");
    }
    assertThat(consume(authority,CredentialWorkflow.Purpose.ACTIVATE,token,flow,null).state()).isEqualTo("ACTIVATED");
  }
  @Test @Tag("SEC-21") void twoActivationTokensSharingOnePreauthOnlyActivateOneAccount() throws Exception {
    var a=credentials.invite("racea"+UUID.randomUUID().toString().replace("-","").substring(0,16));
    var b=credentials.invite("raceb"+UUID.randomUUID().toString().replace("-","").substring(0,16));var flow=authority.begin(null);
    ExecutorService pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
    try {
      List<Future<Boolean>> results=new ArrayList<>();
      for(var token:List.of(a,b))results.add(pool.submit(()->{gate.await();try{consume(authority,CredentialWorkflow.Purpose.ACTIVATE,token,flow,null);return true;}catch(Denied e){assertThat(e.status).isEqualTo(409);return false;}}));
      assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(value("SELECT count(*) FROM identity.app_user WHERE user_id IN ('"+a.userId()+"','"+b.userId()+"') AND state='ACTIVE'")).isEqualTo("1");
      assertThat(value("SELECT count(*) FROM identity.credential_token WHERE user_id IN ('"+a.userId()+"','"+b.userId()+"') AND consumed_at IS NOT NULL")).isEqualTo("1");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-12") void resetDuringPausedLoginKdfCannotIssueAStaleSession() throws Exception {
    Account a=account();var loginFlow=authority.begin(null);var resetFlow=authority.begin(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    PasswordHasher paused=new PasswordHasher() {
      final Argon2CredentialHasher delegate=new Argon2CredentialHasher();
      public String hash(String value){return delegate.hash(value);}
      public boolean matches(String value,String encoded){entered.countDown();await(release);return delegate.matches(value,encoded);}
    };
    var custom=new SessionAuthority(ds,REALM,new CredentialVerifier(paused),new AuthThrottle(ds,new byte[32],100000));
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      var future=pool.submit(()->custom.login(loginFlow.cookie(),loginFlow.view().contextId(),loginFlow.view().csrfToken(),a.login,PASSWORD,"127.10.0.1"));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      consume(authority,CredentialWorkflow.Purpose.RESET,token,resetFlow,UUID.randomUUID()); // no account lock held by KDF
      release.countDown();assertThatThrownBy(()->future.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(Denied.class);
      assertThat(authority.bootstrap(loginFlow.cookie())).isEqualTo(loginFlow.view());
      assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+a.user+"'")).isEqualTo("0");
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-12") void preauthConsumedDuringResetKdfRollsBackTokenAndPassword() throws Exception {
    Account a=account();var flow=authority.begin(null);var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);String before=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    PasswordHasher paused=new PasswordHasher() {
      final Argon2CredentialHasher delegate=new Argon2CredentialHasher();
      public boolean matches(String value,String encoded){return delegate.matches(value,encoded);}
      public String hash(String value){String encoded=delegate.hash(value);entered.countDown();await(release);return encoded;}
    };
    var throttle=new AuthThrottle(ds,new byte[32],100000);
    var custom=new SessionAuthority(ds,REALM,new CredentialVerifier(new Argon2CredentialHasher()),throttle,
        new CredentialService(new CredentialWorkflow(ds,paused),corpus,throttle));
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      var future=pool.submit(()->consume(custom,CredentialWorkflow.Purpose.RESET,token,flow,UUID.randomUUID()));
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var session=login(a,flow);release.countDown();
      assertThatThrownBy(()->future.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(Denied.class);
      assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(before);
      assertThat(value("SELECT consumed_at IS NULL FROM identity.credential_token WHERE user_id='"+a.user+"' AND purpose='RESET'")).isEqualTo("t");
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test @Tag("SEC-21") void credentialFlowsRejectCsrfContextAndInvalidTokensWithoutConsumingPreauth() throws Exception {
    Account a=account();var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);var flow=authority.begin(null);
    long before=Long.parseLong(value("SELECT count(*) FROM identity.auth_throttle"));
    denial(403,()->authority.consume(CredentialWorkflow.Purpose.RESET,flow.cookie(),flow.view().contextId(),"x".repeat(43),token.rawToken(),PASSWORD,"127.11.0.1",UUID.randomUUID()));
    denial(409,()->authority.consume(CredentialWorkflow.Purpose.RESET,flow.cookie(),UUID.randomUUID(),flow.view().csrfToken(),token.rawToken(),PASSWORD,"127.11.0.1",UUID.randomUUID()));
    assertThat(value("SELECT count(*) FROM identity.auth_throttle")).isEqualTo(Long.toString(before));
    denial(401,()->authority.consume(CredentialWorkflow.Purpose.RESET,flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),"x".repeat(43),PASSWORD,"127.11.0.1",UUID.randomUUID()));
    assertThat(authority.bootstrap(flow.cookie())).isEqualTo(flow.view());
    assertThat(value("SELECT credential_epoch FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("1");
  }
  /** Content read trả owner từ DB, không từ request, và không gia hạn idle. */
  @Test @Tag("SEC-01") void canonicalReadBindsFreshNonceOwnerSessionRealmAndDeadlinesWithoutIdleTouch() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(2));
    String idleBefore=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    byte[] bytes=admissionRequest(session,false);var request=WireCodec.strict(bytes);
    var first=authority.admit(bytes,"content","https://127.0.0.1");
    var second=authority.admit(bytes,"content","https://127.0.0.1");
    assertThat(first.id()).isNotEqualTo(second.id()); // same request không cache ALLOW
    assertThat(first.nonce().toString()).isEqualTo(request.get("requestNonce").asString());
    assertThat(first.user()).isEqualTo(a.user);assertThat(first.realm()).isEqualTo(REALM);
    assertThat(first.context()).isEqualTo(session.view().contextId());assertThat(first.epoch()).isEqualTo(1);
    assertThat(first.session().toString()).isEqualTo(value("SELECT session_ref::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"));
    assertThat(first.until()).isEqualTo(first.issued().plusSeconds(15));
    assertThat(first.idle()).isAfter(first.until());assertThat(first.absolute()).isAfter(first.idle());
    assertThat(first.json()).hasSize(20).containsEntry("csrfVerified",false).containsEntry("audience","content")
        .containsEntry("requestBinding",request.get("requestBinding").asString());
    assertThat(first.json().get("issuedAt").toString()).matches(".*\\.[0-9]{3}Z");
    assertThat(first.toString()).doesNotContain(session.cookie(),session.view().csrfToken(),a.user.toString());
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"))
        .isEqualTo(idleBefore);
  }

  /** Draft-save là route chủ động duy nhất đang registered; CSRF hợp lệ mới touch. */
  @Test @Tag("SEC-03") void canonicalDraftSaveVerifiesSynchronizerAndTouchesOnlyItsOwnSession() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(2));
    var other=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(3));
    String otherBefore=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+other.view().contextId()+"'");
    var admitted=authority.admit(admissionRequest(session,true),"learning","https://127.0.0.1");
    assertThat(admitted.csrfVerified()).isTrue();assertThat(admitted.caller()).isEqualTo("learning");
    assertThat(admitted.idle()).isEqualTo(admitted.issued().plus(Duration.ofMinutes(30)));
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+other.view().contextId()+"'"))
        .isEqualTo(otherBefore);
  }

  @Test @Tag("SEC-03") void canonicalBadCsrfAndContextDoNotTouchIdleOrCreateOperation() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(2));
    String before=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    var bad=WireCodec.strict(admissionRequest(session,true)).deepCopy();
    ((tools.jackson.databind.node.ObjectNode)bad).put("csrfToken","w".repeat(43));
    denial(403,()->authority.admit(WireCodec.JSON.writeValueAsBytes(bad),"learning","https://127.0.0.1"));
    ((tools.jackson.databind.node.ObjectNode)bad).put("csrfToken",session.view().csrfToken()).put("expectedContextId",UUID.randomUUID().toString());
    denial(409,()->authority.admit(WireCodec.JSON.writeValueAsBytes(bad),"learning","https://127.0.0.1"));
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"))
        .isEqualTo(before);
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
  }

  @Test @Tag("SEC-01") void canonicalUnknownCookieAndPreauthCannotCreateLearnerAdmission() throws Exception {
    var flow=authority.begin(null);
    denial(401,()->authority.admit(admissionRequest(flow,false),"content","https://127.0.0.1"));
    var unknown=new SessionAuthority.Issued("x".repeat(43),flow.view(),900);
    denial(401,()->authority.admit(admissionRequest(unknown,false),"content","https://127.0.0.1"));
    assertThat(authority.bootstrap(flow.cookie())).isEqualTo(flow.view());
  }

  /** Hết idle không được cứu bởi mutation; deny và hai intent commit trước lỗi401. */
  @Test @Tag("SEC-10") void canonicalExpiredMutationCommitsDenyAndCannotRepeatExpiryIntents() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(31));
    byte[] request=admissionRequest(session,true);
    denial(401,()->authority.admit(request,"learning","https://127.0.0.1"));
    denial(401,()->authority.admit(request,"learning","https://127.0.0.1"));
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("EXPIRED");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("1");
    assertThat(value("SELECT count(*) FROM identity.auth_fence_intent WHERE operation_id IN (SELECT operation_id FROM identity.auth_operation WHERE target_user_id='"+a.user+"')")).isEqualTo("2");
  }

  @Test @Tag("SEC-10") void canonicalAbsoluteExpiryAndOrphanBackingAreDeniedDurably() throws Exception {
    Account a=account();var expired=aged(a,Duration.ofHours(13),Duration.ofMinutes(1));
    denial(401,()->authority.admit(admissionRequest(expired,false),"content","https://127.0.0.1"));
    var orphan=login(a,authority.begin(null));execute(ds,"DELETE FROM identity.spring_session WHERE session_id='"+framework(orphan)+"'");
    denial(401,()->authority.admit(admissionRequest(orphan,false),"content","https://127.0.0.1"));
    assertThat(authority.bootstrap(expired.cookie()).state()).isEqualTo("EXPIRED");
    assertThat(authority.bootstrap(orphan.cookie()).state()).isEqualTo("EXPIRED");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("2");
  }

  @Test @Tag("SEC-02") void canonicalLogoutFencePendingAlreadyDeniesAdmissionWithoutWaitingForAck() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    UUID op=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    denial(401,()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));
    assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("PENDING_FENCES");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("1");
  }

  @Test @Tag("SEC-10") void canonicalShortIdleBudgetDeniesReadWithoutRenewingIt() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofSeconds(1795));
    String before=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    denial(403,()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"))
        .isEqualTo(before);
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
  }

  @Test @Tag("SEC-05") void canonicalWrongRealmFailsClosedWithoutTouchOrLocalExpiry() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    execute(migrator,"UPDATE identity.auth_environment SET realm_id='"+UUID.randomUUID()+"'");
    try{denial(503,()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));}
    finally{execute(migrator,"UPDATE identity.auth_environment SET realm_id='"+REALM+"'");}
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
  }

  /** Admission còn đủ budget nhưng deadline gần hơn15s phải dùng min thực,
   * không chỉ kiểm case hết hạn hoặc mặc định issuedAt+15s. */
  @Test @Tag("SEC-10") void canonicalReadDeadlineIsCappedByIdleAndAbsoluteIndependently() throws Exception {
    Account a=account();var idle=aged(a,Duration.ofMinutes(40),Duration.ofSeconds(1786));
    var idleAdmission=authority.admit(admissionRequest(idle,false),"content","https://127.0.0.1");
    assertThat(idleAdmission.until()).isEqualTo(idleAdmission.idle()).isBefore(idleAdmission.issued().plusSeconds(15));
    var absolute=aged(a,SessionAuthority.ABSOLUTE.minusSeconds(14),Duration.ofMinutes(1));
    var absoluteAdmission=authority.admit(admissionRequest(absolute,false),"content","https://127.0.0.1");
    assertThat(absoluteAdmission.until()).isEqualTo(absoluteAdmission.absolute()).isBefore(absoluteAdmission.issued().plusSeconds(15));
  }

  @Test @Tag("SEC-10") void canonicalMutationInsufficientAbsoluteBudgetRollsBackIdleTouch() throws Exception {
    Account a=account();var session=aged(a,SessionAuthority.ABSOLUTE.minusSeconds(5),Duration.ofMinutes(1));
    String before=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    denial(403,()->authority.admit(admissionRequest(session,true),"learning","https://127.0.0.1"));
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"))
        .isEqualTo(before);
  }

  /** Pause bằng account lock actual PG, chờ thấy waiter chứ không đoán bằng sleep.
   * Fixture đổi epoch/pending trước nhả lock; admission phải đọc state mới, không
   * dùng snapshot đã đọc trước khi blocked. Đây chưa là distributed ACK race. */
  @Test @Tag("SEC-02") void canonicalBlockedAdmissionRereadsCommittedAccountTransition() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try(Connection blocker=ds.getConnection()) {
      blocker.setAutoCommit(false);int pid;
      try(var s=blocker.createStatement();var r=s.executeQuery("SELECT pg_backend_pid()")){r.next();pid=r.getInt(1);}
      try(var s=blocker.createStatement()){s.executeQuery("SELECT user_id FROM identity.app_user WHERE user_id='"+a.user+"' FOR UPDATE").close();}
      var future=pool.submit(()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));
      long cutoff=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
      boolean waiting=false;
      while(System.nanoTime()<cutoff) {
        waiting=Integer.parseInt(value("SELECT count(*) FROM pg_stat_activity WHERE "+pid+"=ANY(pg_blocking_pids(pid))"))>0;
        if(waiting)break;
        Thread.sleep(10);
      }
      assertThat(waiting).as("actual account-lock waiter observed").isTrue();
      try(var s=blocker.createStatement()){s.executeUpdate("UPDATE identity.app_user SET credential_epoch=credential_epoch+1,auth_transition_pending=true WHERE user_id='"+a.user+"'");}
      blocker.commit();
      assertThatThrownBy(()->future.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(Denied.class);
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("EXPIRED");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Nếu intent thứ hai lỗi, toàn bộ local deny/operation phải rollback cùng nhau. */
  @Test @Tag("SEC-12") void canonicalExpiryIntentFailureRollsBackSessionAndOperation() throws Exception {
    Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(31));
    execute(migrator,"CREATE FUNCTION identity.test_admission_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.participant='learning' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_admission_failure BEFORE INSERT ON identity.auth_fence_intent FOR EACH ROW EXECUTE FUNCTION identity.test_admission_failure()");
    try {
      denial(10001,()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));
      assertThat(value("SELECT state FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")).isEqualTo("ACTIVE");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(value("SELECT count(*) FROM identity.spring_session WHERE session_id='"+framework(session)+"'")).isEqualTo("1");
    }finally {
      execute(migrator,"DROP TRIGGER test_admission_failure ON identity.auth_fence_intent");
      execute(migrator,"DROP FUNCTION identity.test_admission_failure()");
    }
    denial(401,()->authority.admit(admissionRequest(session,false),"content","https://127.0.0.1"));
  }

  /** Two ACK ở đây là actual own-store leases + validated fixture bytes; chưa
   * là receiver mTLS/network/three-DB proof. Canonical status giữ layer riêng. */
  @Test void barrierLogoutAtomicCommandsAndBothAcksAreRequired() throws Exception {
    barrierFixture();barrier.verifyRuntime();Account a=account();var session=login(a,authority.begin(null));
    UUID op=coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'")).isEqualTo("2");
    assertThat(barrier.complete(op)).isFalse();ackOne(op);assertThat(barrier.complete(op)).isFalse();ackOne(op);
    assertThat(barrier.complete(op)).isTrue();assertThat(barrier.complete(op)).isTrue();
    assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("COMPLETED");
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKED");
    assertThat(authority.bootstrap(session.cookie()).operationId()).isNull();
    assertThat(value("SELECT count(*) FROM identity.security_audit WHERE correlation_id='"+op+"' AND event_type='AUTH_BARRIER_COMPLETED'")).isEqualTo("1");
  }

  /** Lỗi insert Learning outbox phải rollback cả Content message, op và local deny. */
  @Test void barrierSecondOutboxFailureRollsBackCanonicalDeny() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    execute(migrator,"CREATE FUNCTION identity.test_auth_outbox_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.consumer='learning' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_auth_outbox_failure BEFORE INSERT ON identity.auth_delivery_outbox FOR EACH ROW EXECUTE FUNCTION identity.test_auth_outbox_failure()");
    try {
      denial(10001,()->coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID()));
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
      assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE convert_from(command_bytes,'UTF8')::jsonb->>'userId'='"+a.user+"'")).isEqualTo("0");
    }finally{execute(migrator,"DROP TRIGGER test_auth_outbox_failure ON identity.auth_delivery_outbox");execute(migrator,"DROP FUNCTION identity.test_auth_outbox_failure()");}
  }

  /** Pending/partial ACK không cho rotate initiating cookie. Sau completion tạo
   * flow mới cho B; retry/poll A chỉ chạm operation A, không revoke/touch idle B. */
  @Test void barrierCompletedLogoutAllowsFreshFlowAndOldRetryCannotRevokeNewSession() throws Exception {
    barrierFixture();Account a=account(),b=account();var old=login(a,authority.begin(null));UUID key=UUID.randomUUID();
    UUID op=coordinated.logout(old.cookie(),old.view().contextId(),old.view().csrfToken(),key);
    String count=value("SELECT count(*) FROM identity.auth_preauth");
    denial(409,()->coordinated.begin(old.cookie()));ackOne(op);
    denial(409,()->coordinated.begin(old.cookie()));assertThat(value("SELECT count(*) FROM identity.auth_preauth")).isEqualTo(count);
    ackOne(op);assertThat(barrier.complete(op)).isTrue();
    var fresh=coordinated.begin(old.cookie());assertThat(fresh.cookie()).isNotEqualTo(old.cookie());
    assertThat(fresh.view().state()).isEqualTo("ANONYMOUS");assertThat(fresh.view().contextId()).isNotEqualTo(old.view().contextId());
    var newer=login(b,fresh);String idle=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE cookie_digest=decode('"+framework(newer)+"','hex')");
    assertThat(coordinated.logout(old.cookie(),old.view().contextId(),old.view().csrfToken(),key)).isEqualTo(op);
    assertThat(coordinated.operationStatus(old.cookie(),old.view().contextId(),op)).isEqualTo("COMPLETED");
    denial(409,()->coordinated.logout(newer.cookie(),old.view().contextId(),old.view().csrfToken(),key));
    denial(404,()->coordinated.operationStatus(newer.cookie(),old.view().contextId(),op));
    denial(409,()->coordinated.begin(newer.cookie()));
    assertThat(coordinated.bootstrap(newer.cookie()).userId()).isEqualTo(b.user);
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE cookie_digest=decode('"+framework(newer)+"','hex')")).isEqualTo(idle);
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+b.user+"'")).isEqualTo("0");
  }

  /** Row REVOKED do fixture operator tạo không thay hai durable ACK. Terminal
   * state đơn lẻ không cấp fresh flow và không sửa/reset tombstone cũ. */
  @Test void barrierTerminalSessionWithoutCompletedFenceCannotCreateFreshFlow() throws Exception {
    barrierFixture();var session=login(account(),authority.begin(null));
    execute(migrator,"UPDATE identity.auth_session SET state='REVOKED',csrf_token=NULL WHERE cookie_digest=decode('"+framework(session)+"','hex')");
    String count=value("SELECT count(*) FROM identity.auth_preauth");
    denial(409,()->coordinated.begin(session.cookie()));
    assertThat(value("SELECT count(*) FROM identity.auth_preauth")).isEqualTo(count);
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+session.view().userId()+"'")).isEqualTo("0");
  }

  /** Expiry từ protected access cũng append exact hai messages cùng deny; 401
   * không rollback security effect. EXPIRED chưa đủ ACK vẫn không mở flow mới. */
  @Test void barrierExpiryCommitsTwoCommandsAndCompletedFenceAllowsFreshFlow() throws Exception {
    barrierFixture();Account a=account();var session=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(31));
    denial(401,()->coordinated.admit(admissionRequest(session,true),"learning","https://127.0.0.1"));
    var pending=coordinated.bootstrap(session.cookie());assertThat(pending.state()).isEqualTo("EXPIRED");UUID op=pending.operationId();
    assertThat(op).isNotNull();assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'")).isEqualTo("2");
    assertThat(value("SELECT action FROM identity.auth_operation WHERE operation_id='"+op+"'")).isEqualTo("EXPIRY");
    denial(409,()->coordinated.begin(session.cookie()));ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
    assertThat(login(a,coordinated.begin(session.cookie())).view().state()).isEqualTo("ACTIVE");
    assertThat(coordinated.bootstrap(session.cookie()).state()).isEqualTo("REVOKED");
  }

  @Test void barrierLegacyReconciliationPreservesOperationAndExactBytes() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    UUID op=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'")).isEqualTo("0");
    String digest=value("SELECT encode(command_digest,'hex') FROM identity.auth_operation WHERE operation_id='"+op+"'");
    barrier.reconcile(op);String bytes=value("SELECT string_agg(encode(command_bytes,'hex'),',' ORDER BY consumer) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'");
    barrier.reconcile(op);assertThat(value("SELECT string_agg(encode(command_bytes,'hex'),',' ORDER BY consumer) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'"))
        .isEqualTo(bytes);
    assertThat(value("SELECT encode(command_digest,'hex') FROM identity.auth_operation WHERE operation_id='"+op+"'")).isEqualTo(digest);
    ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
  }

  @Test void barrierPendingCannotClearOrCompleteByDirectSql() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));var flow=authority.begin(null);
    UUID op=consume(coordinated,CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET),flow,UUID.randomUUID()).operationId();
    denial(23514,()->execute(ds,"UPDATE identity.auth_operation SET state='COMPLETED',completed_at=clock_timestamp() WHERE operation_id='"+op+"'"));
    denial(23514,()->execute(ds,"UPDATE identity.app_user SET auth_transition_pending=false,auth_pending_operation=NULL WHERE user_id='"+a.user+"'"));
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKING");
    ackOne(op);assertThat(barrier.complete(op)).isFalse();
    assertThat(value("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("true");
  }

  @Test void barrierResetCurrentEpochCompletesAndFreshLoginUsesNewEpoch() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));var flow=authority.begin(null);
    UUID op=consume(coordinated,CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET),flow,UUID.randomUUID()).operationId();
    assertThat(value("SELECT auth_pending_operation::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(op.toString());
    ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
    assertThat(value("SELECT credential_epoch||':'||auth_transition_pending::text||':'||(auth_pending_operation IS NULL)::text FROM identity.app_user WHERE user_id='"+a.user+"'"))
        .isEqualTo("2:false:true");
    assertThat(value("SELECT state FROM identity.credential_transition WHERE operation_id='"+op+"'")).isEqualTo("COMPLETED");
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKED");
    // Cả consumed initiating preauth và session bị account fence đều được tạo
    // flow mới sau completion; không hồi sinh context/cookie hoặc auto-login.
    assertThat(coordinated.begin(flow.cookie()).cookie()).isNotEqualTo(flow.cookie());
    assertThat(login(a,coordinated.begin(session.cookie())).view().credentialEpoch()).isEqualTo("2");
  }

  /** ACK epoch2 đến sau reset epoch3 không được clear marker của operation mới. */
  @Test void barrierOlderResetAckNeverClearsNewerTransition() throws Exception {
    barrierFixture();Account a=account();login(a,authority.begin(null));
    UUID older=consume(coordinated,CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET),authority.begin(null),UUID.randomUUID()).operationId();
    ackOne(older);ackOne(older);
    UUID newer=consume(coordinated,CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET),authority.begin(null),UUID.randomUUID()).operationId();
    assertThat(barrier.complete(older)).isTrue();
    assertThat(value("SELECT credential_epoch||':'||auth_transition_pending::text||':'||auth_pending_operation::text FROM identity.app_user WHERE user_id='"+a.user+"'"))
        .isEqualTo("3:true:"+newer);
    assertThat(barrier.complete(newer)).isFalse();ackOne(newer);ackOne(newer);assertThat(barrier.complete(newer)).isTrue();
  }

  @Test void barrierCurrentGenerationChangeRejectsOldAckWithoutRetargeting() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    UUID op=coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());ackOne(op);ackOne(op);
    String bytes=value("SELECT string_agg(encode(command_bytes,'hex'),',' ORDER BY consumer) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'");
    UUID next=UUID.randomUUID();execute(migrator,"UPDATE identity.delivery_environment SET content_generation='"+next+"'");
    try {
      denial(503,()->barrier.complete(op));
      var peers=new HashMap<>(GENERATIONS);peers.put("content",next);var restored=new AuthBarrierCoordinator(ds,REALM,GENERATIONS.get("identity"),peers);
      denial(409,()->restored.complete(op));denial(409,()->restored.reconcile(op));
      assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("PENDING_FENCES");
      assertThat(value("SELECT string_agg(encode(command_bytes,'hex'),',' ORDER BY consumer) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'"))
          .isEqualTo(bytes);
    }finally{execute(migrator,"UPDATE identity.delivery_environment SET content_generation='"+GENERATIONS.get("content")+"'");}
    assertThat(barrier.complete(op)).isTrue();
  }

  @Test void barrierCompletionFailureRollsBackOperationAndCanonicalSession() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    UUID op=coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());ackOne(op);ackOne(op);
    execute(migrator,"CREATE FUNCTION identity.test_completion_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='AUTH_BARRIER_COMPLETED' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_completion_failure BEFORE INSERT ON identity.security_audit FOR EACH ROW EXECUTE FUNCTION identity.test_completion_failure()");
    try {
      denial(10001,()->barrier.complete(op));
      assertThat(authority.operationStatus(session.cookie(),session.view().contextId(),op)).isEqualTo("PENDING_FENCES");
      assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKING");
      assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"' AND delivery_status='DELIVERED'")).isEqualTo("2");
    }finally{execute(migrator,"DROP TRIGGER test_completion_failure ON identity.security_audit");execute(migrator,"DROP FUNCTION identity.test_completion_failure()");}
    assertThat(barrier.complete(op)).isTrue();
  }

  @Test void barrierTwoConcurrentCompletersProduceOneDurableEffect() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    UUID op=coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());ackOne(op);ackOne(op);
    ExecutorService pool=Executors.newFixedThreadPool(2);var start=new CyclicBarrier(2);
    try {
      Future<Boolean> first=pool.submit(()->{start.await();return barrier.complete(op);});
      Future<Boolean> second=pool.submit(()->{start.await();return barrier.complete(op);});
      assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
      assertThat(value("SELECT count(*) FROM identity.security_audit WHERE correlation_id='"+op+"' AND event_type='AUTH_BARRIER_COMPLETED'")).isEqualTo("1");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Legacy reset reconciliation không consume token/đổi hash/epoch lần hai. */
  @Test void barrierLegacyCredentialResetReconciliationIsImmutableAndDigestOnly() throws Exception {
    barrierFixture();Account a=account();var session=login(a,authority.begin(null));
    var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    assertThat(credentials.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),PASSWORD,corpus)).isTrue();
    UUID op=UUID.fromString(value("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+a.user+"'"));
    String hash=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    barrier.reconcileTransition(op);barrier.reconcileTransition(op);
    assertThat(value("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("2");
    assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(hash);
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("REVOKING");
    assertThat(value("SELECT initiator_kind||':'||(initiating_cookie_digest IS NULL)::text FROM identity.auth_operation WHERE operation_id='"+op+"'"))
        .isEqualTo("MAINTENANCE:true");
    denial(404,()->authority.operationStatus(session.cookie(),session.view().contextId(),op));
    ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
  }

  @Test void barrierDisableAndEnableAuthCompletionKeepLifecycleGate() throws Exception {
    barrierFixture();Account a=account();login(a,authority.begin(null));
    for(var state:List.of(CredentialWorkflow.AccountState.DISABLED,CredentialWorkflow.AccountState.ACTIVE)) {
      credentials.setState(a.user,state);
      UUID op=UUID.fromString(value("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+a.user+"' ORDER BY credential_epoch DESC LIMIT 1"));
      barrier.reconcileTransition(op);ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
      assertThat(value("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("true");
      assertThat(value("SELECT state FROM identity.credential_transition WHERE operation_id='"+op+"'")).isEqualTo("PENDING");
      denial(23514,()->execute(ds,"UPDATE identity.app_user SET auth_transition_pending=false,auth_pending_operation=NULL WHERE user_id='"+a.user+"'"));
    }
    denial(401,()->login(a,authority.begin(null)));
  }

  @Test void barrierRuntimeVerificationCannotRepairMissingCompletionGuard() throws Exception {
    execute(migrator,"ALTER TABLE identity.auth_operation DISABLE TRIGGER auth_operation_completion_guard");
    try{denial(503,barrier::verifyRuntime);}
    finally{execute(migrator,"ALTER TABLE identity.auth_operation ENABLE TRIGGER auth_operation_completion_guard");}
    barrier.verifyRuntime();
  }

  /** RESET có hai auth ACK nhưng journal từ disable/enable cũ chưa đạt: đóng
   * auth sub-operation, giữ account locked; không mở credential login sớm. */
  @Test void barrierResetAfterEnableCannotBypassPendingLifecycle() throws Exception {
    barrierFixture();Account a=account();login(a,authority.begin(null));
    for(var state:List.of(CredentialWorkflow.AccountState.DISABLED,CredentialWorkflow.AccountState.ACTIVE)) {
      credentials.setState(a.user,state);
      UUID transition=UUID.fromString(value("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+a.user+"' ORDER BY credential_epoch DESC LIMIT 1"));
      barrier.reconcileTransition(transition);ackOne(transition);ackOne(transition);assertThat(barrier.complete(transition)).isTrue();
    }
    UUID reset=consume(coordinated,CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET),authority.begin(null),UUID.randomUUID()).operationId();
    ackOne(reset);ackOne(reset);assertThat(barrier.complete(reset)).isTrue();
    assertThat(value("SELECT auth_transition_pending::text||':'||auth_pending_operation::text FROM identity.app_user WHERE user_id='"+a.user+"'"))
        .isEqualTo("true:"+reset);
    denial(23514,()->execute(ds,"UPDATE identity.app_user SET auth_transition_pending=false,auth_pending_operation=NULL WHERE user_id='"+a.user+"'"));
    denial(401,()->login(a,authority.begin(null)));
  }

  static void barrierFixture() throws Exception {
    // Cô lập bằng operator fixture: operation cũ pause nhưng vẫn được giữ. Runtime
    // không unpause/reset chúng; claim của test mới chỉ thấy exact operation mới.
    execute(migrator,"UPDATE identity.auth_delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL WHERE delivery_status='PENDING'");
  }

  /** Hơn một page legacy bị lỗi INSERT không chặn reset thật phía sau. Poison
   * là fault injection của operator test, không lịch sử runtime được sửa/xóa. */
  @Test void barrierPendingTransitionScanPassesSeventeenPoisonIds()throws Exception {
    barrierFixture();Account a=account();var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    assertThat(credentials.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),PASSWORD,corpus)).isTrue();
    UUID good=UUID.fromString(value("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+a.user+"'"));
    Set<UUID> poison=pendingPoisonIds(1);
    int epoch=1000;for(UUID id:poison)execute(ds,"INSERT INTO identity.credential_transition(operation_id,user_id,credential_epoch,action) VALUES ('"+id+"','"+a.user+"',"+(epoch++)+",'RESET')");
    installPendingPoison(poison,"INSERT");
    try{
      scanPastPoison(AuthPendingWork.Phase.TRANSITIONS,poison,good,23514,barrier::reconcileTransition);
      assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+good+"'")).isEqualTo("2");
      for(UUID id:poison){assertThat(value("SELECT state FROM identity.credential_transition WHERE operation_id='"+id+"'")).isEqualTo("PENDING");assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE operation_id='"+id+"'")).isEqualTo("0");}
    }finally{removePendingPoison();}
  }

  /** Legacy operation thiếu static intents là poison thật: authority reject,
   * không tạo command giả; scan vẫn tới operation có đúng hai intents. */
  @Test void barrierPendingReconcileScanPassesSeventeenPoisonIds()throws Exception {
    barrierFixture();var session=login(account(),authority.begin(null));
    UUID good=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    Set<UUID> poison=pendingPoisonIds(2);for(UUID id:poison)copyPendingOperation(good,id,false);
    scanPastPoison(AuthPendingWork.Phase.RECONCILE,poison,good,503,barrier::reconcile);
    assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+good+"'")).isEqualTo("2");
    for(UUID id:poison){assertThat(value("SELECT state FROM identity.auth_operation WHERE operation_id='"+id+"'")).isEqualTo("PENDING_FENCES");assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+id+"'")).isEqualTo("0");}
  }

  /** Hai own-store ACK fixtures không vượt lỗi COMMIT/UPDATE ở17operations.
   * Good operation phía sau complete; poison giữ target/digest/ACK/pending.
   * Đây là own-PG fault proof, không thay actual participant mTLS ACK. */
  @Test void barrierPendingCompletionScanPassesSeventeenPoisonIds()throws Exception {
    barrierFixture();var session=login(account(),authority.begin(null));
    UUID good=authority.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());
    Set<UUID> poison=pendingPoisonIds(3);
    for(UUID id:poison){copyPendingOperation(good,id,true);barrier.reconcile(id);ackOne(id);ackOne(id);}
    barrier.reconcile(good);ackOne(good);ackOne(good);String digest=value("SELECT encode(command_digest,'hex') FROM identity.auth_operation WHERE operation_id='"+good+"'");
    installPendingPoison(poison,"UPDATE");
    try{
      scanPastPoison(AuthPendingWork.Phase.COMPLETE,poison,good,23514,id->assertThat(barrier.complete(id)).isTrue());
      assertThat(value("SELECT state FROM identity.auth_operation WHERE operation_id='"+good+"'")).isEqualTo("COMPLETED");
      for(UUID id:poison){assertThat(value("SELECT state||':'||encode(command_digest,'hex') FROM identity.auth_operation WHERE operation_id='"+id+"'")).isEqualTo("PENDING_FENCES:"+digest);assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+id+"' AND delivery_status='DELIVERED'")).isEqualTo("2");}
    }finally{removePendingPoison();}
  }

  static Set<UUID> pendingPoisonIds(int phase){
    // UUID có MSB=0 đứng trước operation UUIDv4 theo thứ tự PostgreSQL; từng
    // pha có17ID riêng, không phụ thuộc thứ tự ngẫu nhiên test hoặc UUID Java.
    Set<UUID> ids=new LinkedHashSet<>();for(int i=1;i<=17;i++)ids.add(new UUID(0,phase*100L+i));return ids;
  }
  @FunctionalInterface interface PendingEffect {void apply(UUID id)throws Exception;}
  static void scanPastPoison(AuthPendingWork.Phase phase,Set<UUID> poison,UUID good,int status,PendingEffect effect)throws Exception {
    var scan=new AuthPendingWork(ds,REALM);Set<UUID> seen=new HashSet<>();boolean done=false;
    // Đếm toàn bảng cho upper bound độc lập, không copy predicate production.
    int calls=Integer.parseInt(value("SELECT (SELECT count(*) FROM identity.auth_operation)+(SELECT count(*) FROM identity.credential_transition)"))/16+3;
    for(int i=0;i<calls&&!done;i++)for(UUID id:scan.next(phase)){
      if(poison.contains(id)){denial(status,()->effect.apply(id));seen.add(id);}
      else if(id.equals(good)){effect.apply(id);done=true;}
    }
    assertThat(seen).containsExactlyInAnyOrderElementsOf(poison);assertThat(done).as("Good operation after seventeen poison IDs").isTrue();
  }
  static void copyPendingOperation(UUID template,UUID id,boolean intents)throws Exception {
    // Fixture INSERT riêng, không UPDATE immutable bindings hoặc tắt guard.
    // Mọi clone giữ canonical target đã deny của template, key/ID mới độc lập.
    execute(ds,"INSERT INTO identity.auth_operation(operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,target_session_ref,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants,initiator_kind) SELECT '"+id+"','"+id+"',initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,target_session_ref,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants,initiator_kind FROM identity.auth_operation WHERE operation_id='"+template+"'");
    if(intents)execute(ds,"INSERT INTO identity.auth_fence_intent(operation_id,participant,command_digest) SELECT '"+id+"',participant,command_digest FROM identity.auth_fence_intent WHERE operation_id='"+template+"'");
  }
  static void installPendingPoison(Set<UUID> ids,String event)throws Exception {
    if(!Set.of("INSERT","UPDATE").contains(event))throw new IllegalArgumentException("Fixture event invalid");
    String list=String.join(",",ids.stream().map(id->"'"+id+"'::uuid").toList());
    execute(migrator,"CREATE FUNCTION identity.fixture_pending_scan_poison() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$ BEGIN IF NEW.operation_id IN ("+list+") THEN RAISE EXCEPTION 'Fixture pending scan poison' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
    try{execute(migrator,"CREATE TRIGGER fixture_pending_scan_poison BEFORE "+event+" ON identity.auth_operation FOR EACH ROW EXECUTE FUNCTION identity.fixture_pending_scan_poison()");}
    catch(Exception failure){removePendingPoison();throw failure;}
  }
  static void removePendingPoison()throws Exception {
    execute(migrator,"DROP TRIGGER IF EXISTS fixture_pending_scan_poison ON identity.auth_operation");execute(migrator,"DROP FUNCTION IF EXISTS identity.fixture_pending_scan_poison()");
  }

  /** Actual proof store chỉ có digest. Reserve retry không mint/renew và không
   * touch idle; reference là authorization, không learner reset commit/receipt. */
  @Test void reauthDigestOnlySameIntentReservationHasFixedReferenceAndTtl() throws Exception {
    reauth.verifyRuntime();var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();
    var proof=proof(session,key);assertThat(proof.proof()).matches("[A-Za-z0-9_-]{43}");
    assertThat(proof.toString()).doesNotContain(proof.proof());
    assertThat(value("SELECT encode(proof_digest,'hex') FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'"))
        .isEqualTo(HexFormat.of().formatHex(SessionAuthority.digest(proof.proof())));
    assertThat(value("SELECT extract(epoch FROM expires_at-issued_at)::text FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'"))
        .isEqualTo("300.000000");
    String idle=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    var request=reservation(session,proof,key);var first=reauth.reserve(request,"learning");var second=reauth.reserve(request,"learning");
    assertThat(second).isEqualTo(first);assertThat(first.expiresAt()).isEqualTo(proof.expiresAt());
    assertThat(first.toString()).doesNotContain(proof.proof());assertThat(request.toString()).doesNotContain(proof.proof());
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")).isEqualTo(idle);
    assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_RESERVED' AND correlation_id='"+proof.grantRef()+"'")).isEqualTo("1");
    // No-op UPDATE không dùng cho idempotency: reserved history phải chỉ SELECT.
    denial(23514,()->execute(ds,"UPDATE identity.auth_reauth_grant SET state='RESERVED' WHERE grant_ref='"+proof.grantRef()+"'"));
  }

  /** Thay từng binding riêng, không chỉ checksum sai: proof valid nhưng khác
   * owner/session/context/epoch/realm/key/intent không được reserve hoặc đổi state. */
  @Test void reauthCallerAndEveryIntentBindingAreRequired() throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=proof(session,key);var r=reservation(session,proof,key);
    for(String caller:List.of("identity","content","edge","browser"))denial(403,()->reauth.reserve(r,caller));
    var variants=List.of(
        new ReauthAuthority.Request(r.proof(),UUID.randomUUID(),r.session(),r.context(),r.epoch(),r.realm(),r.purpose(),r.operationKey(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),UUID.randomUUID(),r.context(),r.epoch(),r.realm(),r.purpose(),r.operationKey(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),r.session(),UUID.randomUUID(),r.epoch(),r.realm(),r.purpose(),r.operationKey(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),2,r.realm(),r.purpose(),r.operationKey(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),r.epoch(),UUID.randomUUID(),r.purpose(),r.operationKey(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),r.epoch(),r.realm(),r.purpose(),UUID.randomUUID(),r.intentHash()),
        new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),r.epoch(),r.realm(),r.purpose(),r.operationKey(),"b".repeat(64)));
    for(var variant:variants)denial(409,()->reauth.reserve(variant,"learning"));
    denial(400,()->reauth.reserve(new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),r.epoch(),r.realm(),"LOGOUT_ALL",r.operationKey(),r.intentHash()),"learning"));
    denial(401,()->reauth.reserve(new ReauthAuthority.Request("z".repeat(43),r.user(),r.session(),r.context(),r.epoch(),r.realm(),r.purpose(),r.operationKey(),r.intentHash()),"learning"));
    assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
    assertThat(reauth.reserve(r,"learning").reference()).isNotNull();
  }

  /** Wrong CSRF/context phải dừng trước throttle/KDF; hasher fixture fail ngay
   * nếu bị gọi. Không dùng fixture này thay actual Argon2/password cases khác. */
  @Test void reauthBadCsrfAndContextCannotReachKdfOrThrottle() throws Exception {
    var session=login(account(),authority.begin(null));
    var verifier=new CredentialVerifier(new PasswordHasher(){public String hash(String p){throw new AssertionError("Unexpected hash");}public boolean matches(String p,String h){throw new AssertionError("Unexpected KDF");}});
    var checked=new SessionAuthority(ds,REALM,verifier,new AuthThrottle(ds,new byte[32],100000),null,barrier);
    var flow=new ReauthAuthority(ds,REALM,checked,barrier);String count=value("SELECT count(*) FROM identity.auth_throttle");
    denial(403,()->flow.issueLearningReset(session.cookie(),session.view().contextId(),"z".repeat(43),PASSWORD,"127.3.1.1",UUID.randomUUID(),"a".repeat(64)));
    denial(409,()->flow.issueLearningReset(session.cookie(),UUID.randomUUID(),session.view().csrfToken(),PASSWORD,"127.3.1.1",UUID.randomUUID(),"a".repeat(64)));
    assertThat(value("SELECT count(*) FROM identity.auth_throttle")).isEqualTo(count);
  }

  @Test void reauthWrongPasswordHasDedicatedThrottleAndCannotMint() throws Exception {
    var session=login(account(),authority.begin(null));String before=value("SELECT count(*) FROM identity.auth_reauth_grant");
    for(int attempt=0;attempt<5;attempt++)denial(401,()->reauth.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),"wrong password value long", "127.3.2.1",UUID.randomUUID(),"a".repeat(64)));
    denial(429,()->reauth.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.3.2.1",UUID.randomUUID(),"a".repeat(64)));
    assertThat(value("SELECT count(*) FROM identity.auth_reauth_grant")).isEqualTo(before);
    assertThat(Long.parseLong(value("SELECT count(*) FROM identity.auth_throttle WHERE bucket_kind='REAUTH' AND failed_count=5"))).isGreaterThanOrEqualTo(1);
  }

  /** Insert grant lỗi phải rollback grant/audit/interactive idle cùng callback. */
  @Test void reauthMintFailureRollsBackGrantAndIdle() throws Exception {
    var session=login(account(),authority.begin(null));String idle=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'");
    execute(migrator,"CREATE FUNCTION identity.test_reauth_mint_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END $$");
    execute(migrator,"CREATE TRIGGER test_reauth_mint_failure BEFORE INSERT ON identity.auth_reauth_grant FOR EACH ROW EXECUTE FUNCTION identity.test_reauth_mint_failure()");
    try{denial(503,()->proof(session,UUID.randomUUID()));}
    finally{execute(migrator,"DROP TRIGGER test_reauth_mint_failure ON identity.auth_reauth_grant");execute(migrator,"DROP FUNCTION identity.test_reauth_mint_failure()");}
    assertThat(value("SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id='"+session.view().userId()+"'")).isEqualTo("0");
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")).isEqualTo(idle);
  }

  /** Lỗi audit rollback reserve; mất response/recreate authority sau commit đọc
   * same reference. Không giả recreate Java object là packaged process crash. */
  @Test void reauthReservationRollbackAndRestartCannotRefundOrRetarget() throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=proof(session,key);var r=reservation(session,proof,key);
    execute(migrator,"CREATE FUNCTION identity.test_reauth_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='REAUTH_RESERVED' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_reauth_audit_failure BEFORE INSERT ON identity.security_audit FOR EACH ROW EXECUTE FUNCTION identity.test_reauth_audit_failure()");
    try{denial(10001,()->reauth.reserve(r,"learning"));}
    finally{execute(migrator,"DROP TRIGGER test_reauth_audit_failure ON identity.security_audit");execute(migrator,"DROP FUNCTION identity.test_reauth_audit_failure()");}
    assertThat(value("SELECT state||':'||(reservation_ref IS NULL)::text FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED:true");
    var first=reauth.reserve(r,"learning");var restarted=new ReauthAuthority(ds,REALM,coordinated,barrier);
    assertThat(restarted.reserve(r,"learning")).isEqualTo(first);
    denial(409,()->restarted.reserve(new ReauthAuthority.Request(r.proof(),r.user(),r.session(),r.context(),r.epoch(),r.realm(),r.purpose(),UUID.randomUUID(),r.intentHash()),"learning"));
    denial(42501,()->execute(ds,"DELETE FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'"));
  }

  @Test void reauthExpiredGrantAndChangedEpochAreDenied() throws Exception {
    var a=account();var session=login(a,authority.begin(null));UUID key=UUID.randomUUID();var proof=proof(session,key);
    // Chỉ fixture operator backdate expiry, rồi bật lại immutable trigger trước
    // runtime call. Runtime không được UPDATE TTL hoặc disable trigger.
    execute(migrator,"ALTER TABLE identity.auth_reauth_grant DISABLE TRIGGER auth_reauth_transition_guard");
    try{execute(migrator,"UPDATE identity.auth_reauth_grant SET expires_at=issued_at+interval '1 microsecond' WHERE grant_ref='"+proof.grantRef()+"'");}
    finally{execute(migrator,"ALTER TABLE identity.auth_reauth_grant ENABLE TRIGGER auth_reauth_transition_guard");}
    denial(401,()->reauth.reserve(reservation(session,proof,key),"learning"));
    UUID nextKey=UUID.randomUUID();var next=proof(session,nextKey);
    assertThat(credentials.consume(CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET).rawToken(),PASSWORD,corpus)).isTrue();
    denial(401,()->reauth.reserve(reservation(session,next,nextKey),"learning"));
    denial(23514,()->execute(ds,"UPDATE identity.auth_reauth_grant SET state='RESERVED',reservation_ref='"+UUID.randomUUID()+"',reserved_at=clock_timestamp() WHERE grant_ref='"+next.grantRef()+"'"));
  }

  /** Controlled KDF latch chứng minh logout lấy account/session locks được khi
   * hash còn chạy; sau release callback phải đọc terminal state và không mint. */
  @Test void reauthKdfLogoutRaceCannotMintFromOldSession() throws Exception {
    barrierFixture();var session=login(account(),authority.begin(null));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var verifier=new CredentialVerifier(new PasswordHasher(){public String hash(String p){return "fixture";}public boolean matches(String p,String h){entered.countDown();await(release);return true;}});
    var raceSession=new SessionAuthority(ds,REALM,verifier,new AuthThrottle(ds,new byte[32],100000),null,barrier);
    var race=new ReauthAuthority(ds,REALM,raceSession,barrier);ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      var pending=pool.submit(()->{try{race.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.3.4.1",UUID.randomUUID(),"a".repeat(64));return 0;}catch(Denied denied){return denied.status;}});
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      coordinated.logout(session.cookie(),session.view().contextId(),session.view().csrfToken(),UUID.randomUUID());release.countDown();
      assertThat(pending.get(5,TimeUnit.SECONDS)).isEqualTo(401);
      assertThat(value("SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id='"+session.view().userId()+"'")).isEqualTo("0");
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  @Test void reauthRuntimeGuardAndImmutablePrivilegesFailClosed() throws Exception {
    execute(migrator,"ALTER TABLE identity.auth_reauth_grant DISABLE TRIGGER auth_reauth_transition_guard");
    try{denial(503,reauth::verifyRuntime);}
    finally{execute(migrator,"ALTER TABLE identity.auth_reauth_grant ENABLE TRIGGER auth_reauth_transition_guard");}
    reauth.verifyRuntime();var session=login(account(),authority.begin(null));var proof=proof(session,UUID.randomUUID());
    denial(42501,()->execute(ds,"UPDATE identity.auth_reauth_grant SET intent_digest=decode('"+"b".repeat(64)+"','hex') WHERE grant_ref='"+proof.grantRef()+"'"));
    denial(42501,()->execute(ds,"UPDATE identity.auth_reauth_capacity SET max_grants=1"));
  }

  /** Cùng proof/intent hai connections chỉ có một reservation/audit durable. */
  @Test void reauthConcurrentSameIntentHasOneReservation() throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=proof(session,key);var request=reservation(session,proof,key);
    var start=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      var first=pool.submit(()->{start.await();return reauth.reserve(request,"learning");});
      var second=pool.submit(()->{start.await();return reauth.reserve(request,"learning");});
      assertThat(first.get(5,TimeUnit.SECONDS)).isEqualTo(second.get(5,TimeUnit.SECONDS));
      assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_RESERVED' AND correlation_id='"+proof.grantRef()+"'")).isEqualTo("1");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Reset tăng epoch trong lúc KDF phải thắng snapshot password cũ; callback
   * proof không chạy, dù hasher fixture trả matched. Không consume token lần hai. */
  @Test void reauthKdfEpochChangeRaceCannotMintFromOldCredential() throws Exception {
    Account a=account();var session=login(a,authority.begin(null));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var verifier=new CredentialVerifier(new PasswordHasher(){public String hash(String p){return "fixture";}public boolean matches(String p,String h){entered.countDown();await(release);return true;}});
    var race=new ReauthAuthority(ds,REALM,new SessionAuthority(ds,REALM,verifier,new AuthThrottle(ds,new byte[32],100000),null,barrier),barrier);
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      var pending=pool.submit(()->{try{race.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.3.6.1",UUID.randomUUID(),"a".repeat(64));return 0;}catch(Denied denied){return denied.status;}});
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      assertThat(credentials.consume(CredentialWorkflow.Purpose.RESET,credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET).rawToken(),PASSWORD,corpus)).isTrue();
      release.countDown();assertThat(pending.get(5,TimeUnit.SECONDS)).isEqualTo(401);
      assertThat(value("SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id='"+a.user+"'")).isEqualTo("0");
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Expiry phát hiện sau KDF vẫn commit canonical deny/two messages, không bị
   * verifier rollback chỉ vì caller nhận401. Fixture backdate không runtime API. */
  @Test void reauthKdfExpiryRaceCommitsDenyWithoutMintingProof() throws Exception {
    barrierFixture();var session=login(account(),authority.begin(null));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    var verifier=new CredentialVerifier(new PasswordHasher(){public String hash(String p){return "fixture";}public boolean matches(String p,String h){entered.countDown();await(release);return true;}});
    var race=new ReauthAuthority(ds,REALM,new SessionAuthority(ds,REALM,verifier,new AuthThrottle(ds,new byte[32],100000),null,barrier),barrier);
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try {
      var pending=pool.submit(()->{try{race.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.3.7.1",UUID.randomUUID(),"a".repeat(64));return 0;}catch(Denied denied){return denied.status;}});
      assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
      execute(migrator,"ALTER TABLE identity.auth_session DISABLE TRIGGER session_transition_guard");
      try{execute(migrator,"UPDATE identity.auth_session SET issued_at=clock_timestamp()-interval '2 hours',last_interactive_at=clock_timestamp()-interval '31 minutes' WHERE context_id='"+session.view().contextId()+"'");}
      finally{execute(migrator,"ALTER TABLE identity.auth_session ENABLE TRIGGER session_transition_guard");}
      release.countDown();assertThat(pending.get(5,TimeUnit.SECONDS)).isEqualTo(401);
      var state=coordinated.bootstrap(session.cookie());assertThat(state.state()).isEqualTo("EXPIRED");assertThat(state.operationId()).isNotNull();
      assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+state.operationId()+"'")).isEqualTo("2");
      assertThat(value("SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id='"+session.view().userId()+"'")).isEqualTo("0");
      // Deny đã commit là thành công của cơ chế thu hồi, nhưng yêu cầu reauth
      // vẫn bị từ chối; audit không được ghi nhầm SUCCEEDED cho request này.
      assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_DENIED' AND target_user_id='"+session.view().userId()+"' AND result='DENIED' AND detail_code='SESSION_INVALID'")).isEqualTo("1");
      assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_DENIED' AND target_user_id='"+session.view().userId()+"' AND result<>'DENIED'")).isEqualTo("0");
    }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Actual two connections tranh slot quota cuối: không overshoot. Mock KDF
   * chỉ tránh chi phí hash ở quota test, actual password/throttle có cases riêng. */
  @Test void reauthConcurrentCapacityCannotOvershootGlobalBound() throws Exception {
    var firstSession=login(account(),authority.begin(null));var secondSession=login(account(),authority.begin(null));
    var verifier=new CredentialVerifier(new PasswordHasher(){public String hash(String p){return "fixture";}public boolean matches(String p,String h){return true;}});
    var flow=new ReauthAuthority(ds,REALM,new SessionAuthority(ds,REALM,verifier,new AuthThrottle(ds,new byte[32],100000),null,barrier),barrier);
    long before=Long.parseLong(value("SELECT count(*) FROM identity.auth_reauth_grant"));execute(migrator,"UPDATE identity.auth_reauth_capacity SET max_grants="+(before+1));
    var start=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> first=()->{start.await();try{flow.issueLearningReset(firstSession.cookie(),firstSession.view().contextId(),firstSession.view().csrfToken(),PASSWORD,"127.3.8.1",UUID.randomUUID(),"a".repeat(64));return true;}catch(Denied denied){assertThat(denied.status).isEqualTo(429);return false;}};
      Callable<Boolean> second=()->{start.await();try{flow.issueLearningReset(secondSession.cookie(),secondSession.view().contextId(),secondSession.view().csrfToken(),PASSWORD,"127.3.8.2",UUID.randomUUID(),"a".repeat(64));return true;}catch(Denied denied){assertThat(denied.status).isEqualTo(429);return false;}};
      var a=pool.submit(first);var b=pool.submit(second);assertThat(List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(Long.parseLong(value("SELECT count(*) FROM identity.auth_reauth_grant"))).isEqualTo(before+1);
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();execute(migrator,"UPDATE identity.auth_reauth_capacity SET max_grants=100000");}
  }

  /** Logout-all tăng epoch một lần, chặn mọi phiên/token trước network. Hai ACK
   * ở test own-store là fixture, không thay actual three-app qualification. */
  @Test void sensitiveLogoutAllAtomicallyDeniesAllSessionsAndRetriesWithoutIdleOrCookieEffect()throws Exception {
    barrierFixture();sensitive.verifyRuntime();var a=account();
    var first=login(a,authority.begin(null));var second=login(a,authority.begin(null));
    credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    String oldHash=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    UUID key=UUID.randomUUID();var proof=sensitiveProof(first,key,"LOGOUT_ALL",null);
    String idle=value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+first.view().contextId()+"'");
    UUID op=sensitiveExecute(first,key,"LOGOUT_ALL",proof,null);
    assertThat(sensitiveExecute(first,key,"LOGOUT_ALL",proof,null)).isEqualTo(op);
    assertThat(value("SELECT credential_epoch::text||':'||auth_transition_pending::text||':'||auth_pending_operation::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("2:true:"+op);
    assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(oldHash);
    assertThat(value("SELECT count(*) FROM identity.auth_session WHERE user_id='"+a.user+"' AND state='REVOKING'")).isEqualTo("2");
    assertThat(value("SELECT count(*) FROM identity.spring_session WHERE session_id IN ('"+framework(first)+"','"+framework(second)+"')")).isEqualTo("0");
    assertThat(value("SELECT count(*) FROM identity.credential_token WHERE user_id='"+a.user+"' AND consumed_at IS NULL AND invalidated_at IS NULL")).isEqualTo("0");
    assertThat(value("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+op+"'")).isEqualTo("2");
    assertThat(value("SELECT count(*) FROM identity.credential_transition WHERE user_id='"+a.user+"'")).isEqualTo("0");
    assertThat(value("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+first.view().contextId()+"'")).isEqualTo(idle);
    assertThat(barrier.complete(op)).isFalse();ackOne(op);assertThat(barrier.complete(op)).isFalse();ackOne(op);assertThat(barrier.complete(op)).isTrue();
    var next=login(a,coordinated.begin(first.cookie()));
    denial(409,()->sensitiveExecute(next,key,"LOGOUT_ALL",proof,null));
    assertThat(sensitiveExecute(first,key,"LOGOUT_ALL",proof,null)).isEqualTo(op);
    assertThat(coordinated.bootstrap(next.cookie()).state()).isEqualTo("ACTIVE");
    denial(404,()->coordinated.operationStatus(next.cookie(),next.view().contextId(),op));
  }

  @Test void sensitivePasswordChangeBindsPayloadAndHashesOnlyOneDurableEffect()throws Exception {
    barrierFixture();var a=account();var session=login(a,authority.begin(null));UUID key=UUID.randomUUID();
    String password="different password change 🔐 value";
    var proof=sensitiveProof(session,key,"PASSWORD_CHANGE",password);
    String clientDigest=IdentitySensitiveIntent.digest("PASSWORD_CHANGE",session.view().contextId(),key,password);
    assertThat(value("SELECT encode(intent_digest,'hex') FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isNotEqualTo(clientDigest);
    denial(409,()->sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,"different other password value"));
    UUID op=sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,password);
    String hash=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    assertThat(canonicalVerifier.verify(password,hash,"ACTIVE").accepted()).isTrue();
    assertThat(canonicalVerifier.verify(PASSWORD,hash,"ACTIVE").accepted()).isFalse();
    assertThat(sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,password)).isEqualTo(op);
    assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(hash);
    assertThat(value("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("2");
    assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_CONSUMED' AND correlation_id='"+proof.grantRef()+"'")).isEqualTo("1");
    ackOne(op);ackOne(op);assertThat(barrier.complete(op)).isTrue();
    var flow=coordinated.begin(session.cookie());
    assertThat(coordinated.login(flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),a.login,password,"127.6.2.1").view().credentialEpoch()).isEqualTo("2");
  }

  /** Proof cùng owner nhưng khác session cũng bị deny; Learning không reserve
   * Identity purpose. Sai payload/CSRF/key/context không consume hoặc tăng epoch. */
  @Test void sensitiveEveryBindingAndLearningPurposeAreRequired()throws Exception {
    var a=account();var session=login(a,authority.begin(null));var other=login(a,authority.begin(null));
    UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    denial(401,()->sensitive.execute(other.cookie(),session.view().contextId(),session.view().csrfToken(),key,"LOGOUT_ALL",proof.proof(),null));
    denial(409,()->sensitive.execute(session.cookie(),UUID.randomUUID(),session.view().csrfToken(),key,"LOGOUT_ALL",proof.proof(),null));
    denial(409,()->sensitiveExecute(session,UUID.randomUUID(),"LOGOUT_ALL",proof,null));
    denial(403,()->sensitive.execute(session.cookie(),session.view().contextId(),"x".repeat(43),key,"LOGOUT_ALL",proof.proof(),null));
    denial(409,()->sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,"valid other password value"));
    denial(400,()->sensitiveExecute(session,key,"LOGOUT_ALL",proof,"hidden password value"));
    denial(401,()->sensitive.execute(session.cookie(),session.view().contextId(),session.view().csrfToken(),key,"LOGOUT_ALL","z".repeat(43),null));
    denial(400,()->reauth.reserve(new ReauthAuthority.Request(proof.proof(),a.user,
      UUID.fromString(value("SELECT session_ref::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'")),
      session.view().contextId(),1,REALM,"LOGOUT_ALL",key,"a".repeat(64)),"learning"));
    assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
    assertThat(value("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("1");
  }

  @Test void sensitiveSecondOutboxFailureRollsBackProofHashEpochTokensAndDeny()throws Exception {
    barrierFixture();var a=account();var session=login(a,authority.begin(null));
    credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);UUID key=UUID.randomUUID();
    String password="atomic rollback password value";var proof=sensitiveProof(session,key,"PASSWORD_CHANGE",password);
    String oldHash=value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'");
    execute(migrator,"CREATE FUNCTION identity.test_sensitive_delivery_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.consumer='learning' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_sensitive_delivery_failure BEFORE INSERT ON identity.auth_delivery_outbox FOR EACH ROW EXECUTE FUNCTION identity.test_sensitive_delivery_failure()");
    try{denial(10001,()->sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,password));}
    finally{execute(migrator,"DROP TRIGGER test_sensitive_delivery_failure ON identity.auth_delivery_outbox");execute(migrator,"DROP FUNCTION identity.test_sensitive_delivery_failure()");}
    assertThat(value("SELECT state||':'||(consumed_operation_ref IS NULL)::text FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED:true");
    assertThat(value("SELECT password_hash FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo(oldHash);
    assertThat(value("SELECT credential_epoch::text||':'||auth_transition_pending::text FROM identity.app_user WHERE user_id='"+a.user+"'")).isEqualTo("1:false");
    assertThat(value("SELECT count(*) FROM identity.credential_token WHERE user_id='"+a.user+"' AND consumed_at IS NULL AND invalidated_at IS NULL")).isEqualTo("1");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"'")).isEqualTo("0");
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
    assertThat(sensitiveExecute(session,key,"PASSWORD_CHANGE",proof,password)).isNotNull();
  }

  @Test void sensitiveAuditFailureRollsBackConsumeAndOutboxes()throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    execute(migrator,"CREATE FUNCTION identity.test_sensitive_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_type='REAUTH_CONSUMED' THEN RAISE EXCEPTION 'fixture' USING ERRCODE='P0001'; END IF; RETURN NEW; END $$");
    execute(migrator,"CREATE TRIGGER test_sensitive_audit_failure BEFORE INSERT ON identity.security_audit FOR EACH ROW EXECUTE FUNCTION identity.test_sensitive_audit_failure()");
    try{denial(10001,()->sensitiveExecute(session,key,"LOGOUT_ALL",proof,null));}
    finally{execute(migrator,"DROP TRIGGER test_sensitive_audit_failure ON identity.security_audit");execute(migrator,"DROP FUNCTION identity.test_sensitive_audit_failure()");}
    assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+session.view().userId()+"'")).isEqualTo("0");
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
  }

  @Test void sensitiveConcurrentConsumeAndRestartReturnOneImmutableOperation()throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    ExecutorService pool=Executors.newFixedThreadPool(2);var start=new CyclicBarrier(2);
    try{
      var first=pool.submit(()->{start.await();return sensitiveExecute(session,key,"LOGOUT_ALL",proof,null);});
      var second=pool.submit(()->{start.await();return sensitiveExecute(session,key,"LOGOUT_ALL",proof,null);});
      UUID op=first.get(20,TimeUnit.SECONDS);assertThat(second.get(20,TimeUnit.SECONDS)).isEqualTo(op);
      var restarted=new IdentitySensitiveAuthority(ds,REALM,reauth,barrier,canonicalVerifier,corpus,SENSITIVE_KEY);
      assertThat(restarted.execute(session.cookie(),session.view().contextId(),session.view().csrfToken(),key,"LOGOUT_ALL",proof.proof(),null)).isEqualTo(op);
      assertThat(value("SELECT count(*) FROM identity.security_audit WHERE event_type='REAUTH_CONSUMED' AND correlation_id='"+proof.grantRef()+"'")).isEqualTo("1");
      assertThat(value("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+session.view().userId()+"'")).isEqualTo("2");
      denial(23514,()->execute(ds,"UPDATE identity.auth_reauth_grant SET state='ISSUED',consumed_operation_ref=NULL,consumed_at=NULL WHERE grant_ref='"+proof.grantRef()+"'"));
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  /** Backstop SQL: consume có matching operation nhưng thiếu local deny/two
   * outboxes phải FAIL ở COMMIT, không giữ durable consumed orphan. */
  @Test void sensitiveDirectConsumeCannotCommitWithoutAtomicEffect()throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    UUID operation=UUID.randomUUID();
    try(Connection c=ds.getConnection()) {
      c.setAutoCommit(false);
      try(var s=c.prepareStatement("INSERT INTO identity.auth_operation(operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants) VALUES (?,?,?,?,?,?,2,?,'ACCOUNT','LOGOUT_ALL',?,'r1-static-v1',ARRAY['content','learning'])")) {
        s.setObject(1,operation);s.setObject(2,key);s.setBytes(3,SessionAuthority.digest(session.cookie()));s.setObject(4,session.view().contextId());s.setBytes(5,SessionAuthority.digest(session.view().csrfToken()));s.setObject(6,session.view().userId());s.setObject(7,REALM);s.setBytes(8,new byte[32]);s.executeUpdate();
      }
      try(var s=c.prepareStatement("UPDATE identity.auth_reauth_grant SET state='CONSUMED',consumed_operation_ref=?,consumed_at=clock_timestamp() WHERE grant_ref=?")) {
        s.setObject(1,operation);s.setObject(2,proof.grantRef());assertThat(s.executeUpdate()).isEqualTo(1);
      }
      denial(23514,c::commit);c.rollback();
    }
    assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE operation_id='"+operation+"'")).isEqualTo("0");
  }

  @Test void sensitiveMissingDeferredGuardAndChangedStorageKeyFailClosed()throws Exception {
    execute(migrator,"ALTER TABLE identity.auth_reauth_grant DISABLE TRIGGER auth_reauth_consumed_effect_guard");
    try{denial(503,sensitive::verifyRuntime);}
    finally{execute(migrator,"ALTER TABLE identity.auth_reauth_grant ENABLE TRIGGER auth_reauth_consumed_effect_guard");}
    sensitive.verifyRuntime();var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    byte[] other=new byte[32];Arrays.fill(other,(byte)2);
    var wrongKey=new IdentitySensitiveAuthority(ds,REALM,reauth,barrier,canonicalVerifier,corpus,other);
    denial(409,()->wrongKey.execute(session.cookie(),session.view().contextId(),session.view().csrfToken(),key,"LOGOUT_ALL",proof.proof(),null));
    denial(42501,()->execute(ds,"UPDATE identity.auth_reauth_grant SET intent_digest=decode('"+"b".repeat(64)+"','hex') WHERE grant_ref='"+proof.grantRef()+"'"));
  }

  static ReauthAuthority.IssuedProof sensitiveProof(SessionAuthority.Issued session,UUID key,String purpose,String password)throws SQLException {
    return sensitive.issue(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.6.1.1",key,purpose,
      IdentitySensitiveIntent.digest(purpose,session.view().contextId(),key,password));
  }

  /** Controlled KDF chỉ giữ khoảng giữa precheck và locked-reread; reset thật
   * thắng ở own PG. Hash candidate cũ không consume proof/ghi password lần nữa. */
  @Test void sensitivePasswordKdfResetRaceCannotConsumeOldProof()throws Exception {
    var a=account();var session=login(a,authority.begin(null));UUID key=UUID.randomUUID();
    String password="pending hash candidate password";var proof=sensitiveProof(session,key,"PASSWORD_CHANGE",password);
    var token=credentials.reissue(a.user,CredentialWorkflow.Purpose.RESET);
    var started=new CountDownLatch(1);var resume=new CountDownLatch(1);
    var verifier=new CredentialVerifier(new PasswordHasher(){
      public String hash(String input){started.countDown();await(resume);return new Argon2CredentialHasher().hash(input);}
      public boolean matches(String input,String encoded){return new Argon2CredentialHasher().matches(input,encoded);}
    });
    var held=new IdentitySensitiveAuthority(ds,REALM,reauth,barrier,verifier,corpus,SENSITIVE_KEY);
    ExecutorService pool=Executors.newSingleThreadExecutor();
    try{
      var future=pool.submit(()->{denial(401,()->held.execute(session.cookie(),session.view().contextId(),session.view().csrfToken(),key,"PASSWORD_CHANGE",proof.proof(),password));return true;});
      assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
      assertThat(credentials.consume(CredentialWorkflow.Purpose.RESET,token.rawToken(),PASSWORD,corpus)).isTrue();
      resume.countDown();assertThat(future.get(20,TimeUnit.SECONDS)).isTrue();
      assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
      assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_user_id='"+a.user+"' AND action='PASSWORD_CHANGE'")).isEqualTo("0");
    }finally{resume.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }

  @Test void sensitiveExpiredProofAndCrossPurposeReservationCannotMutate()throws Exception {
    var session=login(account(),authority.begin(null));UUID key=UUID.randomUUID();var proof=sensitiveProof(session,key,"LOGOUT_ALL",null);
    denial(23514,()->execute(ds,"UPDATE identity.auth_reauth_grant SET state='RESERVED',reservation_ref='"+UUID.randomUUID()+"',reserved_at=clock_timestamp() WHERE grant_ref='"+proof.grantRef()+"'"));
    // Operator fixture đặt TTL đã hết; chỉ ISSUED nên chưa có consumed effect.
    // Trigger được khôi phục dù fixture lỗi; runtime không có quyền đổi TTL.
    execute(migrator,"ALTER TABLE identity.auth_reauth_grant DISABLE TRIGGER auth_reauth_transition_guard");
    try{execute(migrator,"UPDATE identity.auth_reauth_grant SET expires_at=issued_at+interval '1 microsecond' WHERE grant_ref='"+proof.grantRef()+"'");}
    finally{execute(migrator,"ALTER TABLE identity.auth_reauth_grant ENABLE TRIGGER auth_reauth_transition_guard");}
    denial(401,()->sensitiveExecute(session,key,"LOGOUT_ALL",proof,null));
    assertThat(value("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.grantRef()+"'")).isEqualTo("ISSUED");
    assertThat(authority.bootstrap(session.cookie()).state()).isEqualTo("ACTIVE");
  }
  static UUID sensitiveExecute(SessionAuthority.Issued session,UUID key,String purpose,ReauthAuthority.IssuedProof proof,String password)throws SQLException {
    return sensitive.execute(session.cookie(),session.view().contextId(),session.view().csrfToken(),key,purpose,proof.proof(),password);
  }

  static ReauthAuthority.IssuedProof proof(SessionAuthority.Issued session,UUID key)throws SQLException {
    return reauth.issueLearningReset(session.cookie(),session.view().contextId(),session.view().csrfToken(),PASSWORD,"127.3.5."+(1+Math.abs(session.view().userId().hashCode()%200)),key,"a".repeat(64));
  }
  static ReauthAuthority.Request reservation(SessionAuthority.Issued session,ReauthAuthority.IssuedProof proof,UUID key)throws SQLException {
    UUID ref=UUID.fromString(value("SELECT session_ref::text FROM identity.auth_session WHERE context_id='"+session.view().contextId()+"'"));
    return new ReauthAuthority.Request(proof.proof(),session.view().userId(),ref,session.view().contextId(),Long.parseLong(session.view().credentialEpoch()),REALM,ReauthAuthority.PURPOSE,key,"a".repeat(64));
  }
  static void ackOne(UUID op) {
    AuthDeliveryStore.Lease lease=barrier.deliveries().claim().orElseThrow();
    var command=lease.command();assertThat(command.operation()).isEqualTo(op);
    var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("receiver",command.consumer());m.put("operationId",op.toString());
    m.put("commandDigest",command.digest());m.put("authRealmId",REALM.toString());m.put("participantGeneration",command.generation().toString());
    m.put("topologyVersion",AuthDeliveryWire.TOPOLOGY);m.put("scope",command.scope());m.put("appliedFloor",Long.toString(command.floor()));
    m.put("sessionRef",command.session()==null?null:command.session().toString());m.put("sessionContextId",command.context()==null?null:command.context().toString());m.put("durable",true);
    assertThat(barrier.deliveries().delivered(lease,WireCodec.JSON.writeValueAsBytes(m))).isTrue();
  }

  /** Exact request fixture tính binding từ method/path/body; không đưa owner vào input. */
  static byte[] admissionRequest(SessionAuthority.Issued session,boolean mutation) {
    String method=mutation?"PUT":"GET",path=mutation?"/api/v1/drafts/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa":"/api/v1/topics/TOP-JAVA-COLLECTIONS/units/unit-1";
    // Hash mutation chỉ là shape của validated receiver body ở lớp test authority.
    // Dùng digest hợp lệ64hex; kiểm exact-body ở receiver có suite riêng.
    String body=mutation?"a".repeat(64):"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    String binding;
    try{binding=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((method+"\n"+path+"\n\n"+body).getBytes(StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    var map=new LinkedHashMap<String,Object>();map.put("schemaVersion",1);map.put("action",mutation?"LEARNING_DRAFT_SAVE":"CONTENT_BODY_READ");
    map.put("audience",mutation?"learning":"content");map.put("requestNonce",UUID.randomUUID().toString());
    map.put("sessionCredential",session.cookie());map.put("expectedContextId",session.view().contextId().toString());
    map.put("method",method);map.put("canonicalPath",path);map.put("query","");map.put("bodySha256",body);map.put("requestBinding",binding);
    if(mutation){map.put("csrfToken",session.view().csrfToken());map.put("origin","https://127.0.0.1");}
    return WireCodec.JSON.writeValueAsBytes(map);
  }

  static SessionAuthority.Consumed consume(SessionAuthority service,CredentialWorkflow.Purpose purpose,CredentialWorkflow.IssuedToken token,SessionAuthority.Issued flow,UUID key) throws Exception {
    return service.consume(purpose,flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),token.rawToken(),PASSWORD,"127.12.0.1",key);
  }
  @Test @Tag("SEC-10") void sweepIsBoundedDoesNotTouchActiveAndCannotRepeatExpiryIntent() throws Exception {
    Account a=account();var active=login(a,authority.begin(null));
    var expired=aged(a,Duration.ofMinutes(40),Duration.ofMinutes(31));
    authority.verifyRuntime();
    authority.sweepExpired(100);
    assertThat(authority.bootstrap(expired.cookie()).state()).isEqualTo("EXPIRED");
    assertThat(authority.bootstrap(active.cookie())).isEqualTo(active.view());
    String count=value("SELECT count(*) FROM identity.auth_operation WHERE target_session_ref=(SELECT session_ref FROM identity.auth_session WHERE context_id='"+expired.view().contextId()+"')");
    authority.sweepExpired(100);
    assertThat(value("SELECT count(*) FROM identity.auth_operation WHERE target_session_ref=(SELECT session_ref FROM identity.auth_session WHERE context_id='"+expired.view().contextId()+"')")).isEqualTo(count).isEqualTo("1");
    assertThatThrownBy(()->authority.sweepExpired(101)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->authority.sweepExpired(0)).isInstanceOf(IllegalArgumentException.class);
  }
  @Test @Tag("SEC-05") void runtimeSchemaCheckDoesNotRepairMissingGuard() throws Exception {
    execute(migrator,"ALTER TABLE identity.auth_session DISABLE TRIGGER session_transition_guard");
    try{denial(503,authority::verifyRuntime);}
    finally{execute(migrator,"ALTER TABLE identity.auth_session ENABLE TRIGGER session_transition_guard");}
    authority.verifyRuntime();
  }
  static void await(CountDownLatch gate) {
    try{if(!gate.await(10,TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("fixture interrupted");}
  }

  static PGSimpleDataSource source(String kind) {
    PGSimpleDataSource s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(CONFIG.getProperty("lab.port"))});
    s.setDatabaseName("identity_db");s.setUser(CONFIG.getProperty("identity."+kind+".user"));s.setPassword(CONFIG.getProperty("identity."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(10);return s;
  }
  static Account account() throws Exception {
    String name="session"+UUID.randomUUID().toString().replace("-","").substring(0,16);
    var token=credentials.invite(name);assertThat(credentials.consume(CredentialWorkflow.Purpose.ACTIVATE,token.rawToken(),PASSWORD,corpus)).isTrue();return new Account(token.userId(),name);
  }
  static SessionAuthority.Issued login(Account a,SessionAuthority.Issued flow) throws Exception {
    return authority.login(flow.cookie(),flow.view().contextId(),flow.view().csrfToken(),a.login,PASSWORD,"127.2."+(Math.abs(a.login.hashCode())%200)+".1");
  }
  static String value(String sql) throws SQLException {
    try(Connection c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}
  }
  static void execute(PGSimpleDataSource source,String sql) throws SQLException {
    try(Connection c=source.getConnection();var s=c.createStatement()){s.execute(sql);}
  }
  interface Checked {void run() throws Exception;}
  static void denial(int status,Checked action) {
    if(status>999)assertThatThrownBy(action::run).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo(status==10001?"P0001":Integer.toString(status)));
    else assertThatThrownBy(action::run).isInstanceOfSatisfying(Denied.class,e->assertThat(e.status).isEqualTo(status));
  }
  static String framework(SessionAuthority.Issued issued){return HexFormat.of().formatHex(SessionAuthority.digest(issued.cookie()));}
  @SuppressWarnings("unchecked") static SessionRepository<Session> backing(Connection c,String id) {
    var repository=new JdbcIndexedSessionRepository(new JdbcTemplate(new SingleConnectionDataSource(c,true)),TransactionOperations.withoutTransaction());
    repository.setTableName("identity.spring_session");repository.setSessionIdGenerator(()->id);return (SessionRepository<Session>)(SessionRepository<?>)repository;
  }
  static SessionAuthority.Issued aged(Account a,Duration issuedAge,Duration idleAge) throws Exception {
    String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().substring(0,32).getBytes(StandardCharsets.US_ASCII));
    // Fixture uses random valid handles and historical rows, not disabling the
    // production transition trigger or changing an active session's issued time.
    UUID context=UUID.randomUUID();String csrf="z".repeat(43),id=HexFormat.of().formatHex(SessionAuthority.digest(raw));
    try(Connection c=ds.getConnection();var s=c.prepareStatement("INSERT INTO identity.auth_session(session_ref,cookie_digest,framework_id,context_id,user_id,credential_epoch,realm_id,state,csrf_token,issued_at,last_interactive_at) VALUES (?,?,?,?,?,1,?,'ACTIVE',?,clock_timestamp()-(? * interval '1 second'),clock_timestamp()-(? * interval '1 second'))")) {
      s.setObject(1,UUID.randomUUID());s.setBytes(2,SessionAuthority.digest(raw));s.setString(3,id);s.setObject(4,context);s.setObject(5,a.user);s.setObject(6,REALM);s.setString(7,csrf);s.setLong(8,issuedAge.toSeconds());s.setLong(9,idleAge.toSeconds());s.executeUpdate();
      var repository=backing(c,id);Session backing=repository.createSession();backing.setMaxInactiveInterval(Duration.ofHours(12));repository.save(backing);
    }
    return new SessionAuthority.Issued(raw,new SessionAuthority.View("ACTIVE",context,csrf,a.user,"1",null),43200);
  }
}
